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
import retrofit2.HttpException
import ru.protonmod.next.data.network.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi
) {
    private var lastServersUpdate: String? = null
    private var cachedServers: List<LogicalServer> = emptyList()

    companion object {
        private const val TAG = "VpnRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Fetch the list of servers considering their current load.
     * Optimized with protocol filtering and If-Modified-Since caching.
     */
    suspend fun getServers(accessToken: String, sessionId: String, userTier: Int = 0): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            
            // 1. Fetch logical servers list with optimizations
            val response = vpnApi.getLogicalServers(
                authorization = bearer, 
                sessionId = sessionId,
                lastModified = lastServersUpdate,
                protocols = "wireguard" // Only fetch what we need
            )

            val serversList = when (response.code()) {
                304 -> {
                    Log.d(TAG, "Servers not modified, using cache")
                    cachedServers
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

            Log.d(TAG, "Servers received: ${serversList.size} (Source: ${if (response.code() == 304) "Cache" else "Network"})")

            val logicalServers = serversList.filter { it.tier <= userTier }
            Log.d(TAG, "Filtered servers (userTier=$userTier): ${logicalServers.size}")
            
            // 2. Fetch server loads
            Log.d(TAG, "Fetching loads for tier: $userTier")
            val loadsResponse = vpnApi.getLoads(bearer, sessionId, userTier)
            val loadsBody = loadsResponse.body()?.string()
            
            val loadsData = if (loadsResponse.isSuccessful && loadsBody != null) {
                try {
                    json.decodeFromString<LoadsResponse>(loadsBody)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode LoadsResponse", e)
                    null
                }
            } else {
                null
            }

            val loadsMap = loadsData?.loads?.associate { it.id to it.load } ?: emptyMap()

            // 3. Map loads to servers
            logicalServers.forEach { logical ->
                val logicalLoad = loadsMap[logical.id]
                if (logicalLoad != null) {
                    logical.averageLoad = logicalLoad
                    logical.servers.forEach { physical ->
                        physical.load = loadsMap[physical.id] ?: logicalLoad
                    }
                } else {
                    var totalLoad = 0
                    var activeServers = 0
                    
                    logical.servers.forEach { physical ->
                        val load = loadsMap[physical.id]
                        if (load != null) {
                            physical.load = load
                            totalLoad += load
                            activeServers++
                        } else {
                            physical.load = 0
                        }
                    }
                    
                    logical.averageLoad = if (activeServers > 0) totalLoad / activeServers else 0
                }
            }

            Result.success(logicalServers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the user's current location as seen by the VPN API.
     */
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

    /**
     * Fetch the list of servers with a timeout and network error handling.
     */
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
            Log.e(TAG, "Request timeout")
            Result.failure(Exception("Request timeout"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch servers: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch the user's VPN profile information (including Tier).
     */
    suspend fun getVpnInfo(accessToken: String, sessionId: String): Result<VpnInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val response = vpnApi.getVpnInfo(bearer, sessionId)
            val body = response.body()?.string()
            
            if (response.isSuccessful && body != null) {
                val vpnInfo = json.decodeFromString<VpnInfoResponse>(body)
                Result.success(vpnInfo)
            } else {
                Result.failure(Exception("Failed to fetch VPN info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers the public key at /vpn/v1/certificate.
     */
    suspend fun registerWireGuardKey(
        accessToken: String,
        sessionId: String,
        publicKeyPem: String
    ): Result<CreateCertificateResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val request = CreateCertificateRequest(clientPublicKey = publicKeyPem)
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            if (response.code == 1000) {
                Result.success(response)
            } else {
                Result.failure(Exception("Proton Cert Error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
