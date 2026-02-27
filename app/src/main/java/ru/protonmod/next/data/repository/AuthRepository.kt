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

import android.util.Base64
import android.util.Log
import com.proton.gopenpgp.srp.Srp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import retrofit2.HttpException
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.*
import ru.protonmod.next.ui.screens.CaptchaRequiredException
import ru.protonmod.next.ui.screens.ProtonErrorResponse
import ru.protonmod.next.utils.DeviceInfoProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: ProtonAuthApi,
    private val vpnRepository: VpnRepository, // Injecting VpnRepository to register certs during auth
    private val sessionDao: SessionDao,
    private val deviceInfoProvider: DeviceInfoProvider
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[Login] Phase 0: Creating Anonymous Session")

            val challengePayload = buildJsonObject {
                putJsonObject("Payload") {
                    putJsonObject("vpn-android-v4-challenge-0") {
                        put("v", deviceInfoProvider.getAppVersion())
                        put("appLang", deviceInfoProvider.getAppLanguage())
                        put("timezone", deviceInfoProvider.getTimezone())
                        put("deviceName", deviceInfoProvider.getDeviceName())
                        put("regionCode", deviceInfoProvider.getRegionCode())
                        put("timezoneOffset", deviceInfoProvider.getTimezoneOffset())
                        put("isJailbreak", deviceInfoProvider.isJailbreak())
                        put("preferredContentSize", deviceInfoProvider.getPreferredContentSize())
                        put("storageCapacity", deviceInfoProvider.getStorageCapacity())
                        put("isDarkmodeOn", deviceInfoProvider.isDarkModeOn())
                        putJsonArray("keyboards") {
                            deviceInfoProvider.getInstalledKeyboards().forEach { add(it) }
                        }
                    }
                }
            }

            val anonSession = authApi.createAnonymousSession(challengePayload, captchaToken)
            val anonToken = anonSession.accessToken
            val anonUid = anonSession.sessionId

            if (anonToken.isNullOrEmpty() || anonUid.isNullOrEmpty()) {
                Log.e(TAG, "[Login] Failed to obtain anonymous session.")
                return@withContext Result.failure(Exception("Failed to get anonymous session"))
            }

            val bearer = "Bearer $anonToken"

            Log.d(TAG, "[Login] Phase 1: Requesting Auth Info")
            val authInfo = authApi.getAuthInfo(bearer, anonUid, AuthInfoRequest(username), captchaToken)
            if (authInfo.code != 1000) return@withContext Result.failure(Exception("Auth info failed: ${authInfo.code}"))

            val auth = Srp.newAuth(4L, username, passwordRaw.toByteArray(), authInfo.salt ?: "", authInfo.modulus ?: "", authInfo.serverEphemeral ?: "")
            val proofs = auth.generateProofs(2048L)

            val loginRequest = LoginRequest(
                username = username,
                clientEphemeral = Base64.encodeToString(proofs.clientEphemeral, Base64.NO_WRAP),
                clientProof = Base64.encodeToString(proofs.clientProof, Base64.NO_WRAP),
                srpSession = authInfo.srpSession ?: "",
                payload = challengePayload["Payload"]?.jsonObject
            )

            Log.d(TAG, "[Login] Phase 2: Performing Login SRP")
            val loginResponse = authApi.performLogin(bearer, anonUid, loginRequest, captchaToken)

            val finalAccessToken = loginResponse.accessToken ?: anonToken
            val finalRefreshToken = loginResponse.refreshToken ?: anonSession.refreshToken ?: ""
            val finalUid = loginResponse.sessionId ?: anonUid

            if (!loginResponse.scopes.contains("twofactor")) {
                Log.d(TAG, "[Login] Registering offline VPN certificate and fetching user tier...")
                val keys = registerAndGetVpnKeys(finalAccessToken, finalUid)
                
                val vpnInfoResult = vpnRepository.getVpnInfo(finalAccessToken, finalUid)
                val userTier = vpnInfoResult.getOrNull()?.vpnInfo?.maxTier ?: 0
                Log.d(TAG, "[Login] Fetched User Tier: $userTier (Result: ${if (vpnInfoResult.isSuccess) "Success" else "Failure: " + vpnInfoResult.exceptionOrNull()?.message})")

                saveSessionLocally(
                    accessToken = finalAccessToken,
                    refreshToken = finalRefreshToken,
                    sessionId = finalUid,
                    userId = loginResponse.userId ?: "",
                    userTier = userTier,
                    wgPrivateKey = keys?.first,
                    wgPublicKeyPem = keys?.second
                )
            }

            Log.d(TAG, "[Login] Success. Scopes: ${loginResponse.scopes}")
            Result.success(loginResponse.copy(
                accessToken = finalAccessToken,
                refreshToken = finalRefreshToken,
                sessionId = finalUid
            ))
        } catch (e: Exception) {
            handleHttpError(e)
        }
    }

    suspend fun verify2FA(
        sessionId: String,
        tempAccessToken: String,
        refreshToken: String,
        totpCode: String
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $tempAccessToken"

            Log.d(TAG, "[2FA] Step 1: Submitting TOTP to auth/v4/2fa")
            val response2fa = authApi.performSecondFactor(bearer, sessionId, SecondFactorRequest(totpCode))

            if (response2fa.code != 1000) {
                return@withContext Result.failure(Exception("2FA rejected: ${response2fa.code}"))
            }

            val fullToken = response2fa.accessToken ?: tempAccessToken
            val fullBearer = "Bearer $fullToken"

            Log.d(TAG, "[2FA] Step 2: Getting User ID at core/v4/users")
            val userResponse = authApi.getUser(fullBearer, sessionId)
            val finalUserId = userResponse.user?.id ?: ""

            Log.d(TAG, "[2FA] Registering offline VPN certificate and fetching user tier...")
            val keys = registerAndGetVpnKeys(fullToken, sessionId)
            
            val vpnInfoResult = vpnRepository.getVpnInfo(fullToken, sessionId)
            val userTier = vpnInfoResult.getOrNull()?.vpnInfo?.maxTier ?: 0
            Log.d(TAG, "[2FA] Fetched User Tier: $userTier (Result: ${if (vpnInfoResult.isSuccess) "Success" else "Failure: " + vpnInfoResult.exceptionOrNull()?.message})")

            saveSessionLocally(
                accessToken = fullToken,
                refreshToken = refreshToken,
                sessionId = sessionId,
                userId = finalUserId,
                userTier = userTier,
                wgPrivateKey = keys?.first,
                wgPublicKeyPem = keys?.second
            )

            Log.d(TAG, "[2FA] Complete. Final UserID: $finalUserId")
            Result.success(response2fa.copy(userId = finalUserId))
        } catch (e: Exception) {
            Log.e(TAG, "[2FA] Critical failure in 2FA flow", e)
            handleHttpError(e)
        }
    }

    /**
     * Generates Ed25519 KeyPair, converts it to proper X.509/WireGuard formats,
     * and registers it with the Proton API to be used later completely offline.
     * Returns Pair(PrivateKeyB64, PublicKeyPem).
     */
    private suspend fun registerAndGetVpnKeys(accessToken: String, sessionId: String): Pair<String, String>? {
        return try {
            val keyPair = com.proton.gopenpgp.ed25519.KeyPair()
            val publicKeyPem = keyPair.publicKeyPKIXPem()
            val wgPrivateKeyB64 = keyPair.toX25519Base64()

            val regResult = vpnRepository.registerWireGuardKey(
                accessToken = accessToken,
                sessionId = sessionId,
                publicKeyPem = publicKeyPem
            )

            if (regResult.isSuccess) {
                Pair(wgPrivateKeyB64, publicKeyPem)
            } else {
                Log.e(TAG, "Failed to register offline certificate: ${regResult.exceptionOrNull()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating offline keys", e)
            null
        }
    }

    private suspend fun saveSessionLocally(
        accessToken: String,
        refreshToken: String,
        sessionId: String,
        userId: String,
        userTier: Int,
        wgPrivateKey: String?,
        wgPublicKeyPem: String?
    ) {
        sessionDao.saveSession(
            SessionEntity(
                accessToken = accessToken,
                refreshToken = refreshToken,
                sessionId = sessionId,
                userId = userId,
                userTier = userTier,
                wgPrivateKey = wgPrivateKey,
                wgPublicKeyPem = wgPublicKeyPem
            )
        )
    }

    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val url = e.response()?.raw()?.request?.url
            Log.e(TAG, "HTTP Error ${e.code()} at URL: $url")
            Log.e(TAG, "Error body: $errorBody")

            if (e.code() == 422 && errorBody != null) {
                try {
                    val parsedError = jsonParser.decodeFromString<ProtonErrorResponse>(errorBody)
                    if (parsedError.code == 9001) {
                        return Result.failure(CaptchaRequiredException(parsedError.details?.webUrl ?: ""))
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to parse error body", ex)
                }
            }
        }
        return Result.failure(e)
    }
}
