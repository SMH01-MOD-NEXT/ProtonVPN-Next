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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import ru.protonmod.next.data.network.*
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServerMapper
import ru.protonmod.next.data.local.SessionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi,
    private val serverDao: ServerDao,
    private val sessionDao: SessionDao
) {
    private var lastServersUpdate: String? = null
    private var cachedServers: List<LogicalServer> = emptyList()

    companion object {
        private const val TAG = "VpnRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun getServers(accessToken: String, sessionId: String, userTier: Int = 0): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            
            // 1. Try to fetch from API
            val response = vpnApi.getLogicalServers(
                authorization = bearer, 
                sessionId = sessionId,
                lastModified = lastServersUpdate,
                protocols = "wireguard"
            )

            val serversList = when (response.code()) {
                304 -> {
                    Log.d(TAG, "Servers not modified (304), using in-memory or DB cache")
                    cachedServers.ifEmpty {
                        // If memory is empty (e.g. app restart), load from DB
                        val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                        cachedServers = dbServers
                        dbServers
                    }
                }
                200 -> {
                    val body = response.body()
                    if (body?.code == 1000) {
                        lastServersUpdate = response.headers()["Last-Modified"]
                        cachedServers = body.logicalServers
                        body.logicalServers
                    } else {
                        return@withContext Result.failure(Exception("API error: ${body?.code}"))
                    }
                }
                else -> return@withContext Result.failure(Exception("Network error: ${response.code()}"))
            }

            if (serversList.isEmpty()) {
                // Last ditch effort: try DB if API failed or returned empty
                val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                if (dbServers.isNotEmpty()) return@withContext Result.success(dbServers.filter { it.tier <= userTier })
                return@withContext Result.failure(Exception("No servers available"))
            }

            val logicalServers = serversList.filter { it.tier <= userTier }
            
            // 2. Fetch server loads (always fresh if possible)
            val loadsResponse = vpnApi.getLoads(bearer, sessionId, userTier)
            if (loadsResponse.isSuccessful) {
                val loadsBody = loadsResponse.body()?.string()
                val loadsData = loadsBody?.let { 
                    try { json.decodeFromString<LoadsResponse>(it) } catch (e: Exception) { null }
                }
                
                val loadsMap = loadsData?.loads?.associate { it.id to it.load } ?: emptyMap()

                logicalServers.forEach { logical ->
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
                        logical.averageLoad = if (activeServers > 0) totalLoad / activeServers else 0
                    }
                }
            }

            Result.success(logicalServers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
            // Fallback to DB on network failure
            val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
            if (dbServers.isNotEmpty()) return@withContext Result.success(dbServers.filter { it.tier <= userTier })
            Result.failure(e)
        }
    }

    suspend fun getUserLocation(accessToken: String, sessionId: String): Result<String> = withContext(Dispatchers.IO) {
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

    suspend fun getServersWithTimeout(
        accessToken: String,
        sessionId: String,
        timeoutSeconds: Long,
        userTier: Int = 0
    ): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutSeconds * 1000) {
                getServers(accessToken, sessionId, userTier)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Request timeout, trying cache")
            val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
            if (dbServers.isNotEmpty()) Result.success(dbServers.filter { it.tier <= userTier })
            else Result.failure(Exception("Request timeout"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVpnInfo(accessToken: String, sessionId: String): Result<VpnInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val response = vpnApi.getVpnInfo(bearer, sessionId)
            val body = response.body()?.string()
            
            Log.d(TAG, "getVpnInfo raw body: $body")
            
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
    ): Result<CreateCertificateResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val request = CreateCertificateRequest(clientPublicKey = publicKeyPem)
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            Log.d(TAG, "registerWireGuardKey response code: ${response.code}, cert length: ${response.certificate?.length ?: 0}")

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
            Log.e(TAG, "Error in registerWireGuardKey", e)
            Result.failure(e)
        }
    }
}
