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
    companion object {
        private const val TAG = "VpnRepository"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Fetch the list of servers considering their current load.
     */
    suspend fun getServers(accessToken: String, sessionId: String, userTier: Int = 0): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            
            // 1. Fetch logical servers list
            val serversResponse = vpnApi.getLogicalServers(bearer, sessionId)
            Log.d(TAG, "getLogicalServers response code: ${serversResponse.code}, total: ${serversResponse.logicalServers.size}")

            if (serversResponse.code != 1000) {
                return@withContext Result.failure(Exception("Failed to fetch servers: ${serversResponse.code}"))
            }

            val logicalServers = serversResponse.logicalServers.filter { it.tier <= userTier }
            Log.d(TAG, "Filtered servers (userTier=$userTier): ${logicalServers.size}")
            
            if (logicalServers.isNotEmpty()) {
                Log.d(TAG, "Sample LogicalServer ID: ${logicalServers[0].id}")
                if (logicalServers[0].servers.isNotEmpty()) {
                    Log.d(TAG, "Sample PhysicalServer ID: ${logicalServers[0].servers[0].id}")
                }
            }

            // 2. Fetch server loads
            Log.d(TAG, "Fetching loads for tier: $userTier")
            val loadsResponse = vpnApi.getLoads(bearer, sessionId, userTier)
            val loadsBody = loadsResponse.body()?.string()
            Log.d(TAG, "Loads API response code: ${loadsResponse.code()}, body: $loadsBody")
            
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
            Log.d(TAG, "Mapped loads count: ${loadsMap.size}")
            if (loadsMap.isNotEmpty()) {
                Log.d(TAG, "Sample load ID: ${loadsMap.keys.first()}")
            }

            // 3. Map loads to servers
            var logicalWithLoad = 0
            var physicalMatches = 0
            logicalServers.forEach { logical ->
                // First, try to match the logical server directly
                val logicalLoad = loadsMap[logical.id]
                if (logicalLoad != null) {
                    logical.averageLoad = logicalLoad
                    logicalWithLoad++
                    
                    // If we have logical load, maybe physical servers should also have it?
                    // Or maybe the API returns physical server loads in that same list?
                    logical.servers.forEach { physical ->
                        physical.load = loadsMap[physical.id] ?: logicalLoad
                    }
                } else {
                    // Fallback to physical server matching
                    var totalLoad = 0
                    var activeServers = 0
                    
                    logical.servers.forEach { physical ->
                        val load = loadsMap[physical.id]
                        if (load != null) {
                            physical.load = load
                            totalLoad += load
                            activeServers++
                            physicalMatches++
                        } else {
                            physical.load = 0
                        }
                    }
                    
                    if (activeServers > 0) {
                        logical.averageLoad = totalLoad / activeServers
                        logicalWithLoad++
                    } else {
                        logical.averageLoad = 0
                    }
                }
            }
            Log.d(TAG, "Mapped loads for $logicalWithLoad logical servers (Total physical matches: $physicalMatches)")

            Result.success(logicalServers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
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
                Log.d(TAG, "Raw VPN Info Response: $body")
                val vpnInfo = json.decodeFromString<VpnInfoResponse>(body)
                Result.success(vpnInfo)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to fetch VPN info: ${response.code()}, body: $errorBody")
                Result.failure(Exception("Failed to fetch VPN info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching VPN info", e)
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

            Log.d(TAG, "Registering key at /vpn/v1/certificate")
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            if (response.code == 1000) {
                Result.success(response)
            } else {
                Result.failure(Exception("Proton Cert Error: ${response.code}"))
            }
        } catch (e: Exception) {
            if (e is HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "Cert registration failed: $errorBody")
            }
            Result.failure(e)
        }
    }
}
