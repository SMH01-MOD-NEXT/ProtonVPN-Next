package ru.protonmod.next.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.ProtonVpnApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi
) {
    companion object {
        private const val TAG = "VpnRepository"
    }

    /**
     * Fetch all available logical servers from Proton API.
     */
    suspend fun getServers(accessToken: String, sessionId: String): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val response = vpnApi.getLogicalServers(bearer, sessionId)

            if (response.code == 1000) {
                // Filter for free servers (Tier 0) for testing, or return all
                val freeServers = response.logicalServers.filter { it.tier == 0 }
                Result.success(freeServers)
            } else {
                Result.failure(Exception("Failed to fetch servers, code: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching servers", e)
            Result.failure(e)
        }
    }
}