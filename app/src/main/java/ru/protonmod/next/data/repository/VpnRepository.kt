package ru.protonmod.next.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.protonmod.next.data.network.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi
) {
    companion object {
        private const val TAG = "VpnRepository"
    }

    suspend fun getServers(accessToken: String, sessionId: String): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"
            val response = vpnApi.getLogicalServers(bearer, sessionId)
            if (response.code == 1000) {
                Result.success(response.logicalServers.filter { it.tier == 0 })
            } else {
                Result.failure(Exception("Failed to fetch servers: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers the public key at /vpn/v1/certificate.
     * Error 2001 "Invalid EC key" usually means the server expects a specific PEM
     * or a raw Base64 without the ASN.1 prefix we tried before.
     */
    suspend fun registerWireGuardKey(
        accessToken: String,
        sessionId: String,
        publicKeyPem: String
    ): Result<CreateCertificateResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"

            // Мы больше не оборачиваем ключ вручную.
            // AmneziaVpnManager передает готовый Ed25519 PKIX PEM, как ожидает сервер.
            val request = CreateCertificateRequest(clientPublicKey = publicKeyPem)

            Log.d(TAG, "Registering key at /vpn/v1/certificate with PEM:\n$publicKeyPem")
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            if (response.code == 1000) {
                Result.success(response)
            } else {
                Result.failure(Exception("Proton Cert Error: ${response.code}"))
            }
        } catch (e: Exception) {
            if (e is HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "Cert registration failed (${e.code()}): $errorBody")

                // If it's still 400/2001, it's possible the server doesn't want PEM at all,
                // but just the raw Base64 key.
            }
            Result.failure(e)
        }
    }
}