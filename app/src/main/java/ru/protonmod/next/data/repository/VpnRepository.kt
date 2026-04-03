/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.repository

import io.sentry.SentryLevel
import ru.protonmod.next.utils.ProtonLogger
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import ru.protonmod.next.data.network.*
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServerMapper
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.data.local.CityTranslationDao
import ru.protonmod.next.data.local.CityTranslationEntity
import ru.protonmod.next.data.local.CityCacheEntity
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi,
    private val serverDao: ServerDao,
    private val sessionDao: SessionDao,
    private val serversCacheDao: ServersCacheDao,
    private val cityTranslationDao: CityTranslationDao,
    private val profileDao: ProfileDao,
    private val recentConnectionDao: RecentConnectionDao,
    private val cityRepository: CityRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val managerScope: CoroutineScope
) {
    private var autoUpdateJob: Job? = null
    private val fetchMutex = Mutex()

    // Variable for storing the currently executing fetch request
    private var activeFetch: Deferred<Result<List<LogicalServer>>>? = null
    private var cachedServers: List<LogicalServer> = emptyList()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    companion object {
        private const val TAG = "VpnRepository"
        private val json = Json { ignoreUnknownKeys = true }
        private const val CACHE_DURATION_MILLIS = 60 * 60 * 1000L // 1 hour
        private const val CITY_CACHE_DURATION_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        private const val AUTO_UPDATE_INTERVAL_MINUTES = 20L
        private const val AUTO_UPDATE_STARTUP_DELAY_MILLIS = 5_000L // 5 seconds
    }

    fun startAutoUpdate() {
        if (autoUpdateJob?.isActive == true) return

        autoUpdateJob = managerScope.launch {
            ProtonLogger.i(TAG, "Starting periodic server load/list auto-update loop")
            // Delay the first cycle so it does not contend with MainActivity/MainViewModel
            // initialization for Room database resources during app startup, which could
            // cause lock contention and an ANR on the main thread.
            delay(AUTO_UPDATE_STARTUP_DELAY_MILLIS)
            while (isActive) {
                val session = withContext(dispatcherProvider.io()) { sessionDao.getSession() }
                if (session != null) {
                    ProtonLogger.d(TAG, "Auto-update: Fetching fresh server data for user tier ${session.userTier}")
                    getServers(
                        session.accessToken,
                        session.sessionId,
                        session.userTier,
                        forceRefresh = false
                    )
                } else {
                    ProtonLogger.w(TAG, "Auto-update: No active session, skipping this cycle")
                }
                delay(TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))
            }
        }
    }

    fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    fun getServersFlow(): Flow<List<LogicalServer>> {
        return serverDao.getServersFlow().map { entities ->
            // Extract tier from the current active session dynamically.
            val userTier = sessionDao.getSession()?.userTier ?: 0
            val servers = entities
                .map { ServerMapper.toDomain(it) }
                .filter { it.tier <= userTier } // Filter dynamically based on session tier
            
            // Localize cities
            servers.forEach { server ->
                server.localizedCity = cityRepository.getLocalizedCityName(
                    server.exitCountry, 
                    server.city
                )
            }
            servers
        }.flowOn(dispatcherProvider.io()) // Ensure the entire map block (including DB access) runs on the IO dispatcher,
        // regardless of the collector's context. This prevents unsafe database access from
        // the main thread, which can cause JNI/native crashes (SIGSEGV) in the Android Runtime.
    }

    suspend fun getCachedServers(): List<LogicalServer> = withContext(dispatcherProvider.io()) {
        val userTier = sessionDao.getSession()?.userTier ?: 0
        val servers = serverDao.getAllServers()
            .map { ServerMapper.toDomain(it) }
            .filter { it.tier <= userTier } // Filter dynamically based on session tier
        
        servers.forEach { server ->
            server.localizedCity = cityRepository.getLocalizedCityName(
                server.exitCountry,
                server.city
            )
        }
        servers
    }

    suspend fun getServers(
        accessToken: String,
        sessionId: String,
        userTier: Int = 0,
        forceRefresh: Boolean = false
    ): Result<List<LogicalServer>> {
        val deferred = fetchMutex.withLock {
            if (activeFetch != null && !forceRefresh) {
                ProtonLogger.d(TAG, "Joining existing servers fetch request")
                activeFetch!!
            } else {
                _isUpdating.value = true
                val newFetch = managerScope.async {
                    try {
                        performGetServers(accessToken, sessionId, userTier, forceRefresh)
                    } finally {
                        fetchMutex.withLock {
                            activeFetch = null
                            _isUpdating.value = false
                        }
                    }
                }
                activeFetch = newFetch
                newFetch
            }
        }

        return try {
            deferred.await()
        } catch (e: CancellationException) {
            // Re-throw CancellationException so caller knows they were cancelled,
            // but the background async task in managerScope continues.
            throw e
        }
    }

    /**
     * Triggers a server update in the application-level background scope.
     * Use this when you don't need to wait for the result immediately (e.g., during login).
     */
    fun refreshServersBackground(accessToken: String, sessionId: String, userTier: Int) {
        managerScope.launch {
            getServers(accessToken, sessionId, userTier)
        }
    }

    private suspend fun performGetServers(
        accessToken: String,
        sessionId: String,
        userTier: Int,
        forceRefresh: Boolean
    ): Result<List<LogicalServer>> = withContext(dispatcherProvider.io()) {
        val startTime = System.currentTimeMillis()
        try {
            val now = System.currentTimeMillis()

            // Ensure city translations are up-to-date at the start of any sync
            refreshCityTranslations(accessToken, sessionId)

            val cacheInfo = serversCacheDao.getCacheInfo()

            val shouldCheckApi = forceRefresh || cacheInfo == null || now > cacheInfo.expiresAt
            val isStale = cacheInfo != null && (now - cacheInfo.cachedAt > TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))

            ProtonLogger.d(TAG, "Server sync check: force=$forceRefresh, hasCache=${cacheInfo != null}, expired=${now > (cacheInfo?.expiresAt ?: 0)}, stale=$isStale")

            if (!shouldCheckApi && !isStale) {
                val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                if (dbServers.isNotEmpty()) {
                    val result = dbServers.filter { it.tier <= userTier }
                    cachedServers = result
                    ProtonLogger.i(TAG, "Returning ${result.size} servers from local cache (API skip)")
                    return@withContext Result.success(result)
                }
            }

            val bearer = "Bearer $accessToken"
            val ifModifiedSince = if (!forceRefresh) cacheInfo?.lastModified else null

            // Refresh city translations whenever we fetch servers
            refreshCityTranslations(accessToken, sessionId)

            ProtonLogger.i(TAG, "Fetching servers from Proton API... (If-Modified-Since: $ifModifiedSince)")
            val response = vpnApi.getLogicalServers(
                authorization = bearer,
                sessionId = sessionId,
                lastModified = ifModifiedSince,
                protocols = "wireguard",
                userTier = userTier
            )

            val (serversList, newLastModified) = when (response.code()) {
                304 -> {
                    ProtonLogger.i(TAG, "Proton API: Servers not modified (304). Re-using existing DB entries.")
                    val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                    dbServers to cacheInfo?.lastModified
                }
                200 -> {
                    val body = response.body()
                    if (body?.code == 1000) {
                        ProtonLogger.i(TAG, "Proton API: Received ${body.logicalServers.size} logical servers")
                        ProtonLogger.addSentryBreadcrumb(TAG, "VPN Repository: Servers Updated (${body.logicalServers.size})", SentryLevel.INFO, "vpn.repo")
                        body.logicalServers to response.headers()["Last-Modified"]
                    } else {
                        ProtonLogger.e(TAG, "Proton API Error: Code ${body?.code}")
                        return@withContext Result.failure(Exception("API error: ${body?.code}"))
                    }
                }
                else -> {
                    ProtonLogger.w(TAG, "Proton API: Unexpected response code ${response.code()}")
                    val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                    if (dbServers.isNotEmpty()) {
                        ProtonLogger.i(TAG, "Falling back to DB servers due to API error")
                        return@withContext Result.success(dbServers.filter { it.tier <= userTier })
                    }
                    return@withContext Result.failure(Exception("Network error: ${response.code()}"))
                }
            }

            if (serversList.isEmpty()) {
                ProtonLogger.w(TAG, "Proton API: Server list is empty in response")
                return@withContext Result.failure(Exception("No servers available"))
            }

            // Fetch server loads and merge with current list
            ProtonLogger.d(TAG, "Fetching server loads for ${serversList.size} servers...")
            val loadsResponse = try {
                vpnApi.getLoads(bearer, sessionId, userTier)
            } catch (e: Exception) {
                ProtonLogger.w(TAG, "Failed to initiate loads request: ${e.message}")
                null
            }

            if (loadsResponse?.isSuccessful == true) {
                val loadsBody = loadsResponse.body()?.string()
                val loadsData = loadsBody?.let {
                    try { json.decodeFromString<LoadsResponse>(it) } catch (e: Exception) { 
                        ProtonLogger.e(TAG, "Failed to parse loads JSON", e)
                        null 
                    }
                }

                val loadsMap = loadsData?.loads?.associate { it.id to it.load } ?: emptyMap()
                ProtonLogger.d(TAG, "Successfully updated loads for ${loadsMap.size} server IDs")

                serversList.forEach { logical ->
                    val logicalLoad = loadsMap[logical.id]
                    if (logicalLoad != null) {
                        logical.averageLoad = logicalLoad
                        logical.servers.forEach { it.load = loadsMap[it.id] ?: logicalLoad }
                    } else {
                        var totalLoad = 0
                        var activeServers = 0
                        logical.servers.forEach { physical ->
                            val load = loadsMap[physical.id]
                            if (load != null) {
                                physical.load = load
                                totalLoad += load
                                activeServers++
                            }
                        }
                        if (activeServers > 0) logical.averageLoad = totalLoad / activeServers
                    }
                }
            } else {
                ProtonLogger.w(TAG, "Failed to fetch fresh server loads (HTTP ${loadsResponse?.code()}). Keeping existing loads.")
                val dbServers = serverDao.getAllServers().associateBy({ it.id }, { it.averageLoad })
                serversList.forEach { it.averageLoad = dbServers[it.id] ?: 0 }
            }

            // Save to DB AFTER fetching loads, ensuring the DB has the latest load values
            val entities = serversList.map { ServerMapper.toEntity(it) }
            serverDao.insertServers(entities)
            ProtonLogger.d(TAG, "Saved ${entities.size} servers to local database")

            // Update cache metadata
            val newCacheInfo = ServersCacheEntity(
                cachedAt = now,
                expiresAt = now + CACHE_DURATION_MILLIS,
                lastModified = newLastModified
            )
            serversCacheDao.saveCacheInfo(newCacheInfo)

            val logicalServers = serversList.filter { it.tier <= userTier }
            
            // Localize cities for the result
            logicalServers.forEach { server ->
                server.localizedCity = cityRepository.getLocalizedCityName(
                    server.exitCountry,
                    server.city
                )
            }

            cachedServers = logicalServers
            
            // Metrics
            val duration = System.currentTimeMillis() - startTime
            Sentry.metrics().distribution("server_fetch_latency", duration.toDouble())
            Sentry.metrics().count("server_fetch_success", 1.0)
            
            Result.success(logicalServers)
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Critical error in performGetServers", e)
            
            // Metrics
            Sentry.metrics().count("server_fetch_error", 1.0)

            val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
            if (dbServers.isNotEmpty()) Result.success(dbServers.filter { it.tier <= userTier })
            else Result.failure(e)
        }
    }

    suspend fun getUserLocation(accessToken: String, sessionId: String): Result<String> = withContext(dispatcherProvider.io()) {
        try {
            val response = vpnApi.getUserLocation("Bearer $accessToken", sessionId)
            val body = response.body()?.string()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Failed to get location: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVpnInfo(accessToken: String, sessionId: String): Result<VpnInfoResponse> = withContext(dispatcherProvider.io()) {
        try {
            val bearer = "Bearer $accessToken"
            
            // Refresh city translations whenever we refresh vpn info or servers
            // This ensures city names stay localized if the user changes system language
            refreshCityTranslations(accessToken, sessionId)

            val response = vpnApi.getVpnInfo(bearer, sessionId)
            val body = response.body()?.string()

            ProtonLogger.d(TAG, "getVpnInfo raw body: $body")

            if (response.isSuccessful && body != null) {
                Result.success(json.decodeFromString<VpnInfoResponse>(body))
            } else {
                Result.failure(Exception("Failed to fetch VPN info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWireGuardKey(
        accessToken: String,
        sessionId: String,
        publicKeyPem: String
    ): Result<CreateCertificateResponse> = withContext(dispatcherProvider.io()) {
        try {
            val bearer = "Bearer $accessToken"
            val request = CreateCertificateRequest(clientPublicKey = publicKeyPem)
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            ProtonLogger.d(TAG, "registerWireGuardKey response code: ${response.code}, cert length: ${response.certificate?.length ?: 0}")

            if (response.code == 1000) {
                val cert = response.certificate
                if (cert != null) {
                    sessionDao.updateCertificate(cert)
                }
                Result.success(response)
            } else {
                Result.failure(Exception("Proton Cert Error: ${response.code}"))
            }
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Error in registerWireGuardKey", e)
            Result.failure(e)
        }
    }

    suspend fun clearCache() = withContext(dispatcherProvider.io()) {
        ProtonLogger.d(TAG, "Clearing VPN cache and user data...")
        serverDao.clearAllServers()
        serversCacheDao.clearCacheInfo()
        cityTranslationDao.clearAll()
        cityTranslationDao.clearCacheInfo()
        profileDao.deleteAllProfiles()
        recentConnectionDao.clearHistory()
        cityRepository.clearCache()
        cachedServers = emptyList()
    }

    private suspend fun refreshCityTranslations(accessToken: String, sessionId: String) {
        try {
            val languageTag = java.util.Locale.getDefault().toLanguageTag()
            val now = System.currentTimeMillis()
            
            // Check if we already have fresh translations for this language
            val lastUpdated = cityTranslationDao.getLastUpdated(languageTag) ?: 0L
            val isExpired = now - lastUpdated > CITY_CACHE_DURATION_MILLIS
            
            if (!isExpired && cityTranslationDao.getCount(languageTag) > 0) {
                ProtonLogger.d(TAG, "City translations for $languageTag are fresh, skipping fetch")
                return
            }

            ProtonLogger.i(TAG, "Fetching city translations for $languageTag...")
            val response = vpnApi.getServerCities("Bearer $accessToken", sessionId, languageTag)
            
            val entities = mutableListOf<CityTranslationEntity>()
            response.cities.forEach { (countryCode, cityMap) ->
                cityMap.forEach { (englishName, localizedName) ->
                    if (localizedName != null) {
                        entities.add(
                            CityTranslationEntity(
                                countryCode = countryCode,
                                englishName = englishName,
                                localizedName = localizedName,
                                languageCode = languageTag
                            )
                        )
                    }
                }
            }
            
            if (entities.isNotEmpty()) {
                // Use upsertTranslations to atomically clear old translations and insert new ones
                // in a single transaction, avoiding N+1 query patterns.
                cityTranslationDao.upsertTranslations(languageTag, entities)
                cityTranslationDao.saveCacheInfo(CityCacheEntity(languageTag, now))
                cityRepository.clearCache()
                ProtonLogger.i(TAG, "Saved ${entities.size} city translations for $languageTag")
            }
        } catch (e: Exception) {
            ProtonLogger.w(TAG, "Failed to refresh city translations: ${e.message}")
        }
    }
}
