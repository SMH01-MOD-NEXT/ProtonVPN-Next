package ru.protonmod.next.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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
                // Возвращаем все сервера, либо фильтруем it.tier == 0 для бесплатных
                val freeServers = response.logicalServers.filter { it.tier == 0 }
                Result.success(freeServers)
            } else {
                Result.failure(Exception("Failed to fetch servers, code: ${response.code}"))
            }
        } catch (e: Exception) {
            if (e is HttpException) {
                val errBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "HTTP Error ${e.code()} when fetching servers: $errBody")
            } else {
                Log.e(TAG, "Error fetching servers", e)
            }
            Result.failure(e)
        }
    }
}