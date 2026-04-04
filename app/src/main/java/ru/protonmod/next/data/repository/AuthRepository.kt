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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import retrofit2.HttpException
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.network.*
import ru.protonmod.next.ui.screens.CaptchaRequiredException
import ru.protonmod.next.ui.screens.ProtonErrorResponse
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.vpn.AmneziaVpnManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import java.net.SocketTimeoutException
import java.net.ConnectException

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: ProtonAuthApi,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val cryptoWrapper: CryptoWrapper,
    private val dispatcherProvider: DispatcherProvider,
    private val amneziaVpnManager: Provider<AmneziaVpnManager>
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val jsonParser = Json { ignoreUnknownKeys = true }
        private val REFRESH_DEBOUNCE_MS = 60000L // 1 minute
        private val FORCE_LOGOUT_HTTP_CODES = listOf(400, 401, 422)
    }

    private var lastRefreshTime: Long = 0L

    private var pendingAnonToken: String? = null
    private var pendingAnonUid: String? = null
    private var pendingAuthInfo: AuthInfoResponse? = null
    private var pendingUsername: String? = null

    // Cache the challenge payload to ensure cryptographic hash matches during CAPTCHA retry
    private var pendingChallengePayload: JsonObject? = null

    /**
     * SupervisorJob for auth operations that allows cancellation of pending login/anonymous operations.
     * Prevents JNI reference leaks when activity is destroyed mid-login.
     */
    private val authJob = SupervisorJob()

    /**
     * Cancel all pending authentication operations.
     * Called when ViewModel is cleared to prevent JNI reference leaks.
     */
    fun cancelPendingOperations() {
        ProtonLogger.d(TAG, "Cancelling pending auth operations")
        authJob.cancel()
        clearPendingAuth()
    }

    /**
     * Resets temporary authentication state and cached payloads.
     */
    fun clearPendingAuth() {
        pendingAnonToken = null
        pendingAnonUid = null
        pendingAuthInfo = null
        pendingUsername = null
        pendingChallengePayload = null
    }

    fun getPendingUid(): String? = pendingAnonUid

    suspend fun exportSession(): String? = withContext(dispatcherProvider.io()) {
        sessionDao.getSession()?.let { jsonParser.encodeToString(it) }
    }

    suspend fun loginBySession(session: SessionEntity): Result<Unit> = withContext(dispatcherProvider.io()) {
        try {
            sessionDao.saveSession(session)
            vpnRepository.refreshServersBackground(session.accessToken, session.sessionId, session.userTier)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears local session and stops background tasks.
     */
    suspend fun logout() = withContext(dispatcherProvider.io()) {
        ProtonLogger.d(TAG, "Logging out user...")
        
        // 1. Disconnect VPN first
        try {
            amneziaVpnManager.get().disconnect()
        } catch (e: Exception) {
            ProtonLogger.w(TAG, "Failed to disconnect VPN during logout: ${e.message}")
        }

        // 2. Notify server (best effort)
        try {
            sessionDao.getSession()?.let { session ->
                if (session.accessToken.isNotEmpty()) {
                    authApi.performLogout("Bearer ${session.accessToken}", session.sessionId)
                    ProtonLogger.i(TAG, "Server-side logout successful")
                }
            }
        } catch (e: Exception) {
            ProtonLogger.w(TAG, "Server-side logout failed: ${e.message}")
        }

        // 3. Clear local state
        vpnRepository.stopAutoUpdate()
        vpnRepository.clearCache()
        sessionDao.clearSession()
        clearPendingAuth()
    }

    /**
     * Main login flow using SRP (Secure Remote Password) protocol.
     * Handles Captcha verification by refreshing sessions if a token is provided.
     */
    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = withContext(dispatcherProvider.io()) {
        try {
            ProtonLogger.i(TAG, "Starting SRP login flow for user: $username (Captcha: ${captchaToken != null})")
            ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Start Login ($username)", SentryLevel.INFO, "auth.flow")

            if (pendingUsername != username) {
                clearPendingAuth()
                pendingUsername = username
            }

            val tokenType = if (captchaToken != null) "captcha" else null

            // Use cached payload if available to guarantee consistent hash for CAPTCHA validation
            val challengePayload = pendingChallengePayload ?: buildChallengePayload().also { pendingChallengePayload = it }

            // Only create a new session if we don't have one cached.
            // If we are retrying after a CAPTCHA, we MUST reuse the existing session ID
            // because the captcha token is cryptographically bound to it.
            if (pendingAnonToken == null || pendingAnonUid == null) {
                ProtonLogger.d(TAG, "[Login] Phase 0: Creating Anonymous Session")
                val anonSession = authApi.createAnonymousSession(challengePayload, captchaToken, tokenType)
                pendingAnonToken = anonSession.accessToken
                pendingAnonUid = anonSession.sessionId
            }

            val anonToken = pendingAnonToken ?: throw Exception("Missing anonymous token")
            val anonUid = pendingAnonUid ?: throw Exception("Missing anonymous uid")
            val bearer = "Bearer $anonToken"

            if (pendingAuthInfo == null) {
                ProtonLogger.d(TAG, "[Login] Phase 1: Requesting Auth Info")
                val authInfo = authApi.getAuthInfo(bearer, anonUid, AuthInfoRequest(username), captchaToken, tokenType)
                if (authInfo.code != 1000) return@withContext Result.failure(Exception("Auth info failed: ${authInfo.code}"))
                pendingAuthInfo = authInfo
            }

            val authInfo = pendingAuthInfo!!
            
            // Validate SRP parameters before proceeding
            if (authInfo.salt.isNullOrEmpty() || authInfo.modulus.isNullOrEmpty() || authInfo.serverEphemeral.isNullOrEmpty()) {
                ProtonLogger.e(TAG, "[Login] Invalid SRP parameters from server")
                return@withContext Result.failure(Exception("Invalid security parameters from server"))
            }

            val proofs = cryptoWrapper.generateSrpProofs(
                username = username,
                passwordRaw = passwordRaw.toByteArray(),
                salt = authInfo.salt,
                modulus = authInfo.modulus,
                serverEphemeral = authInfo.serverEphemeral
            )

            val loginRequest = LoginRequest(
                username = username,
                clientEphemeral = proofs.clientEphemeral,
                clientProof = proofs.clientProof,
                srpSession = authInfo.srpSession ?: "",
                payload = challengePayload["Payload"]?.jsonObject
            )

            ProtonLogger.d(TAG, "[Login] Phase 2: Performing Login SRP")
            val loginResponse = authApi.performLogin(bearer, anonUid, loginRequest, captchaToken, tokenType)

            clearPendingAuth()

            val finalAccessToken = loginResponse.accessToken ?: anonToken
            val finalRefreshToken = loginResponse.refreshToken ?: ""
            val finalUid = loginResponse.sessionId ?: anonUid

            // If 2FA is not required, proceed to complete setup
            if (!loginResponse.scopes.contains("twofactor")) {
                ProtonLogger.d(TAG, "[Login] Completing authentication. Registering VPN cert...")
                val keys = registerAndGetVpnKeys(finalAccessToken, finalUid)

                val vpnInfoResult = vpnRepository.getVpnInfo(finalAccessToken, finalUid)
                val userTier = vpnInfoResult.getOrNull()?.vpnInfo?.maxTier ?: 0

                saveSessionLocally(
                    accessToken = finalAccessToken,
                    refreshToken = finalRefreshToken,
                    sessionId = finalUid,
                    userId = loginResponse.userId ?: "",
                    userTier = userTier,
                    wgPrivateKey = keys?.first,
                    wgPublicKeyPem = keys?.second,
                    wgCertificate = keys?.third
                )
                
                vpnRepository.refreshServersBackground(finalAccessToken, finalUid, userTier)
            }

            ProtonLogger.d(TAG, "[Login] Success. Scopes: ${loginResponse.scopes}")
            ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Success (Scopes: ${loginResponse.scopes})", SentryLevel.INFO, "auth.flow")
            Result.success(loginResponse.copy(
                accessToken = finalAccessToken,
                refreshToken = finalRefreshToken,
                sessionId = finalUid
            ))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            
            // Handle network timeouts explicitly to prevent JNI reference leaks
            if (e is SocketTimeoutException || e is ConnectException) {
                ProtonLogger.w(TAG, "[Login] Network timeout error: ${e.message}")
                clearPendingAuth()
                ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Network Timeout (${e.message})", SentryLevel.WARNING, "auth.flow")
                return@withContext Result.failure(e)
            }
            
            if (e !is HttpException) ProtonLogger.e(TAG, "[Login] Exception thrown", e)
            ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Failed (${e.message})", SentryLevel.ERROR, "auth.flow")
            handleHttpError(e)
        }
    }

    /**
     * Anonymous login flow (Guest login).
     */
    suspend fun loginAnonymous(captchaToken: String? = null): Result<LoginResponse> = withContext(dispatcherProvider.io()) {
        try {
            val tokenType = if (captchaToken != null) "captcha" else null

            // Use cached payload if available to guarantee consistent hash for CAPTCHA validation
            val challengePayload = pendingChallengePayload ?: buildChallengePayload().also { pendingChallengePayload = it }

            ProtonLogger.d(TAG, "[AnonymousLogin] Starting flow. Have Captcha: ${captchaToken != null}")

            // Reusing existing session if available to avoid 12087 error.
            if (pendingAnonToken == null || pendingAnonUid == null) {
                ProtonLogger.d(TAG, "[AnonymousLogin] Requesting initial anonymous session")
                val anonSession = authApi.createAnonymousSession(challengePayload, captchaToken, tokenType)
                pendingAnonToken = anonSession.accessToken
                pendingAnonUid = anonSession.sessionId
            }

            val anonToken = pendingAnonToken ?: throw Exception("Failed to get anonymous session")
            val anonUid = pendingAnonUid ?: throw Exception("Failed to get anonymous UID")
            val bearer = "Bearer $anonToken"

            ProtonLogger.d(TAG, "[AnonymousLogin] Upgrading to credentialless session using UID: $anonUid")
            val response = authApi.performLoginLess(bearer, anonUid, challengePayload, captchaToken, tokenType)

            if (response.code == 1000) {
                ProtonLogger.d(TAG, "[AnonymousLogin] Success. Registering VPN cert...")

                clearPendingAuth()

                val finalAccessToken = response.accessToken ?: anonToken
                val finalUid = response.sessionId ?: anonUid
                val keys = registerAndGetVpnKeys(finalAccessToken, finalUid)

                saveSessionLocally(
                    accessToken = finalAccessToken,
                    refreshToken = response.refreshToken ?: "",
                    sessionId = finalUid,
                    userId = response.userId ?: "",
                    userTier = 0,
                    wgPrivateKey = keys?.first,
                    wgPublicKeyPem = keys?.second,
                    wgCertificate = keys?.third
                )

                vpnRepository.refreshServersBackground(finalAccessToken, finalUid, 0)
                Result.success(response.copy(accessToken = finalAccessToken, sessionId = finalUid))
            } else {
                Result.failure(Exception("Guest login failed: Code ${response.code}"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            
            // Handle network timeouts explicitly to prevent JNI reference leaks
            if (e is SocketTimeoutException || e is ConnectException) {
                ProtonLogger.w(TAG, "[AnonymousLogin] Network timeout error: ${e.message}")
                clearPendingAuth()
                return@withContext Result.failure(e)
            }
            
            if (e !is HttpException) ProtonLogger.e(TAG, "[AnonymousLogin] Exception thrown", e)
            handleHttpError(e)
        }
    }

    /**
     * Refreshes user session using refreshToken.
     * Includes debounce logic and handles force logout on specific HTTP error codes.
     */
    suspend fun refreshSession(sessionId: String, refreshToken: String): Result<LoginResponse> = withContext(dispatcherProvider.io()) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            ProtonLogger.i(TAG, "[Refresh] Debouncing session refresh")
            return@withContext Result.failure(Exception("Debounced"))
        }

        try {
            ProtonLogger.i(TAG, "[Refresh] Attempting session refresh (SID: $sessionId)")
            val refreshRequest = RefreshSessionRequest(uid = sessionId, refreshToken = refreshToken)
            val refreshResponse = authApi.refreshSession(refreshRequest)

            if (refreshResponse.code == 1000 && refreshResponse.accessToken != null) {
                lastRefreshTime = System.currentTimeMillis()
                
                // Update local session
                val currentSession = sessionDao.getSession()
                if (currentSession != null && currentSession.sessionId == sessionId) {
                    val updatedSession = currentSession.copy(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken ?: currentSession.refreshToken
                    )
                    sessionDao.saveSession(updatedSession)
                    ProtonLogger.i(TAG, "[Refresh] Session updated successfully")
                }
                Result.success(refreshResponse)
            } else {
                ProtonLogger.e(TAG, "[Refresh] API rejected refresh: Code ${refreshResponse.code}")
                Result.failure(Exception("Refresh failed with code ${refreshResponse.code}"))
            }
        } catch (e: Exception) {
            if (e is HttpException && e.code() in FORCE_LOGOUT_HTTP_CODES) {
                ProtonLogger.w(TAG, "[Refresh] Force logout triggered by HTTP ${e.code()}")
                logout()
            }
            Result.failure(e)
        }
    }

    /**
     * Verifies Two-Factor Authentication TOTP code.
     */
    suspend fun verify2FA(
        sessionId: String,
        tempAccessToken: String,
        refreshToken: String,
        totpCode: String
    ): Result<LoginResponse> = withContext(dispatcherProvider.io()) {
        try {
            val bearer = "Bearer $tempAccessToken"
            val response2fa = authApi.performSecondFactor(bearer, sessionId, SecondFactorRequest(totpCode))

            if (response2fa.code != 1000) {
                return@withContext Result.failure(Exception("2FA rejected: ${response2fa.code}"))
            }

            val fullToken = response2fa.accessToken ?: tempAccessToken
            val fullBearer = "Bearer $fullToken"

            val userResponse = authApi.getUser(fullBearer, sessionId)
            val finalUserId = userResponse.user?.id ?: ""

            val keys = registerAndGetVpnKeys(fullToken, sessionId)
            val vpnInfoResult = vpnRepository.getVpnInfo(fullToken, sessionId)
            val userTier = vpnInfoResult.getOrNull()?.vpnInfo?.maxTier ?: 0

            saveSessionLocally(
                accessToken = fullToken,
                refreshToken = refreshToken,
                sessionId = sessionId,
                userId = finalUserId,
                userTier = userTier,
                wgPrivateKey = keys?.first,
                wgPublicKeyPem = keys?.second,
                wgCertificate = keys?.third
            )

            vpnRepository.refreshServersBackground(fullToken, sessionId, userTier)
            Result.success(response2fa.copy(userId = finalUserId))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e !is HttpException) ProtonLogger.e(TAG, "[verify2FA] Exception thrown", e)
            handleHttpError(e)
        }
    }

    private fun buildChallengePayload(): JsonObject {
        return buildJsonObject {
            putJsonObject("Payload") {
                putJsonObject("vpn-android-v4-challenge-0") {
                    // Added missing polymorphic type required by Proton's backend deserializer
                    put("type", "me.proton.core.challenge.data.frame.ChallengeFrame.Device")
                    put("v", deviceInfoProvider.getAppVersion())
                    put("appLang", deviceInfoProvider.getAppLanguage())
                    put("timezone", deviceInfoProvider.getTimezone())
                    put("deviceName", deviceInfoProvider.getDeviceHash())
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
    }

    private suspend fun registerAndGetVpnKeys(accessToken: String, sessionId: String): Triple<String, String, String>? {
        return try {
            val vpnKeyPair = cryptoWrapper.generateVpnKeyPair()
            val publicKeyPem = vpnKeyPair.publicKeyPem
            val wgPrivateKeyB64 = vpnKeyPair.privateKeyX25519

            val regResult = vpnRepository.registerWireGuardKey(accessToken, sessionId, publicKeyPem)

            if (regResult.isSuccess) {
                val cert = regResult.getOrNull()?.certificate
                Triple(wgPrivateKeyB64, publicKeyPem, cert ?: "")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveSessionLocally(
        accessToken: String, refreshToken: String, sessionId: String, userId: String,
        userTier: Int, wgPrivateKey: String?, wgPublicKeyPem: String?, wgCertificate: String?
    ) {
        sessionDao.saveSession(
            SessionEntity(
                accessToken = accessToken, refreshToken = refreshToken, sessionId = sessionId,
                userId = userId, userTier = userTier, wgPrivateKey = wgPrivateKey,
                wgPublicKeyPem = wgPublicKeyPem, wgCertificate = wgCertificate
            )
        )
    }

    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val code = e.code()

            if (code == 422 && errorBody != null) {
                try {
                    val parsedError = jsonParser.decodeFromString<ProtonErrorResponse>(errorBody)
                    // 9001 = Needs Captcha
                    if (parsedError.code == 9001) {
                        val url = parsedError.details?.webUrl ?: ""
                        val token = parsedError.details?.humanVerificationToken ?: ""
                        ProtonLogger.w(TAG, "CAPTCHA Verification Required. Token extracted.")
                        return Result.failure(CaptchaRequiredException(url, token, getPendingUid()))
                    }
                    // 12087 = Captcha validation failed due to payload mismatch or session reset.
                    // This happens when the server-side anonymous session was invalidated (e.g., after
                    // the app went to background/foreground) while the captcha token was still bound
                    // to it. We clear all stale state and request a brand-new captcha challenge so
                    // the next attempt uses a fresh, valid session.
                    if (parsedError.code == 12087) {
                        ProtonLogger.e(TAG, "Captcha validation failed (12087): session invalidated. Clearing state and requesting a new challenge.")
                        clearPendingAuth()
                        val freshUrl = parsedError.details?.webUrl ?: ""
                        val freshToken = parsedError.details?.humanVerificationToken ?: ""
                        return Result.failure(CaptchaRequiredException(freshUrl, freshToken, null))
                    }
                } catch (ex: Exception) {
                    ProtonLogger.w(TAG, "Failed to parse 422 error body: ${ex.message}")
                }
                return Result.failure(Exception("HTTP 422: $errorBody"))
            }
            return Result.failure(Exception("HTTP $code: ${errorBody ?: e.message()}"))
        }
        return Result.failure(e)
    }
}
