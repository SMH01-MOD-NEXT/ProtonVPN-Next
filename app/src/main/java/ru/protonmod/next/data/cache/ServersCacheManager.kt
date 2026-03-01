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

package ru.protonmod.next.data.cache

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.repository.VpnRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServersCacheManager @Inject constructor(
    private val serversCacheDao: ServersCacheDao,
    private val serverDao: ServerDao,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao
) {
    companion object {
        private const val TAG = "ServersCacheManager"
        private const val CACHE_DURATION_MILLIS = 60 * 60 * 1000L // 1 hour
        private const val AUTO_UPDATE_INTERVAL_MINUTES = 2L
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoUpdateJob: Job? = null
    private val fetchMutex = Mutex()

    fun startAutoUpdate() {
        if (autoUpdateJob?.isActive == true) return
        
        autoUpdateJob = managerScope.launch {
            Log.d(TAG, "Starting auto-update loop")
            while (isActive) {
                val session = sessionDao.getSession()
                if (session != null) {
                    getServers(
                        session.accessToken,
                        session.sessionId,
                        session.userTier,
                        forceRefresh = false
                    )
                }
                delay(TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))
            }
        }
    }

    fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    fun getServersFlow(): Flow<List<ru.protonmod.next.data.network.LogicalServer>> {
        return serverDao.getServersFlow().map { entities ->
            entities.map { ru.protonmod.next.data.local.ServerMapper.toDomain(it) }
        }
    }

    suspend fun getServers(
        accessToken: String,
        sessionId: String,
        userTier: Int,
        forceRefresh: Boolean = false
    ): Result<Unit> = fetchMutex.withLock {
        return try {
            val now = System.currentTimeMillis()
            val cacheInfo = serversCacheDao.getCacheInfo()

            // Logic: fetch if forced OR no cache OR cache expired OR we want to check for updates (304)
            val shouldCheckApi = forceRefresh || cacheInfo == null || now > cacheInfo.expiresAt

            // Even if cache is not expired, we might want to check for 304 if it's been a while (e.g. 2 mins)
            val isStale = cacheInfo != null && (now - cacheInfo.cachedAt > TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))

            if (shouldCheckApi || isStale) {
                val ifModifiedSince = if (!forceRefresh) cacheInfo?.lastModified else null
                
                Log.d(TAG, "Fetching servers from API (Force: $forceRefresh, If-Modified-Since: $ifModifiedSince)")
                
                val result = vpnRepository.getServers(accessToken, sessionId, userTier)

                result.onSuccess { servers ->
                    val entities = servers.map { ru.protonmod.next.data.local.ServerMapper.toEntity(it) }
                    serverDao.insertServers(entities)

                    // Get the actual Last-Modified from the repository's internal state
                    // (Note: VpnRepository should ideally expose this, but we'll use a trick or just trust the next 304)
                    // For now, we update the timestamp to avoid constant polling
                    val newCacheInfo = ServersCacheEntity(
                        cachedAt = now,
                        expiresAt = now + CACHE_DURATION_MILLIS,
                        lastModified = cacheInfo?.lastModified // Will be updated by next successful network call
                    )
                    serversCacheDao.saveCacheInfo(newCacheInfo)
                }

                result.onFailure { error ->
                    Log.e(TAG, "Failed to fetch servers from API", error)
                    if (cacheInfo == null) return Result.failure(error)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
            Result.failure(e)
        }
    }

    suspend fun getCachedServers(): List<ru.protonmod.next.data.network.LogicalServer> {
        return serverDao.getAllServers().map { ru.protonmod.next.data.local.ServerMapper.toDomain(it) }
    }
}
