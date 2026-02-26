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
     * Получить список серверов с таймаутом и обработкой ошибок сети.
     * Используется для кэширования и фонового обновления.
     * Обрабатывает timeout, EOF, ConnectionRefused ошибки.
     */
    suspend fun getServersWithTimeout(
        accessToken: String,
        sessionId: String,
        timeoutSeconds: Long
    ): Result<List<LogicalServer>> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $accessToken"

            // Применяем таймаут для запроса
            val response = withTimeout(timeoutSeconds * 1000) {
                vpnApi.getLogicalServers(bearer, sessionId)
            }

            if (response.code == 1000) {
                Log.d(TAG, "Servers fetched successfully")
                Result.success(response.logicalServers.filter { it.tier == 0 })
            } else {
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Request timeout after timeout seconds")
            Result.failure(Exception("Request timeout"))
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Socket timeout")
            Result.failure(Exception("Socket timeout"))
        } catch (e: ConnectException) {
            Log.e(TAG, "Connection refused")
            Result.failure(Exception("Connection refused"))
        } catch (e: EOFException) {
            Log.e(TAG, "EOF error - connection broken")
            Result.failure(Exception("EOF error"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch servers: ${e.javaClass.simpleName}: ${e.message}")
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