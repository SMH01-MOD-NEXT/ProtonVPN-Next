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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.SentryLevel
import ru.protonmod.next.utils.ProtonLogger
import kotlinx.coroutines.CancellationException
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
import ru.protonmod.next.utils.crypto.VpnKeyPair
import ru.protonmod.next.vpn.AmneziaVpnManager
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import java.net.SocketTimeoutException
import java.net.ConnectException

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: ProtonAuthApi,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val cryptoWrapper: CryptoWrapper,
    private val dispatcherProvider: DispatcherProvider,
    private val amneziaVpnManager: Provider<AmneziaVpnManager>,
    private val authNativeBridge: AuthNativeBridge
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val jsonParser = Json { ignoreUnknownKeys = true }
        private val REFRESH_DEBOUNCE_MS = 60000L // 1 minute
        private val FORCE_LOGOUT_HTTP_CODES = listOf(400, 401, 422)
    }

    private var lastRefreshTime: Long = 0L

    @Volatile
    private var pendingAnonToken: String? = null
    @Volatile
    private var pendingAnonUid: String? = null
    @Volatile
    private var pendingAuthInfo: AuthInfoResponse? = null
    @Volatile
    private var pendingUsername: String? = null

    // Cache the challenge payload to ensure cryptographic hash matches during CAPTCHA retry
    @Volatile
    private var pendingChallengePayload: JsonObject? = null

    private val authMutex = Mutex()

    /**
     * SupervisorJob for auth operations that allows cancellation of pending login/anonymous operations.
     * Prevents JNI reference leaks when activity is destroyed mid-login.
     */
    private var authJob = SupervisorJob()

    /**
     * Cancel all pending authentication operations.
     * Called when ViewModel is cleared to prevent JNI reference leaks.
     */
    fun cancelPendingOperations() {
        ProtonLogger.d(TAG, "Cancelling pending auth operations")
        authJob.cancel()
        authJob = SupervisorJob()
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
            vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier)
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
    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = authMutex.withLock {
        withContext(dispatcherProvider.io() + authJob) {
            try {
                ProtonLogger.i(TAG, "Starting NATIVE SRP login flow")
                
                val nativeResult = authNativeBridge.login(username, passwordRaw, captchaToken)
                
                if (!nativeResult.success) {
                    if (nativeResult.captchaRequired) {
                        return@withContext Result.failure(CaptchaRequiredException(nativeResult.captchaUrl, nativeResult.captchaToken, nativeResult.sessionId))
                    }
                    val errorMessage = nativeResult.error.ifEmpty { "Native login failed with code ${nativeResult.code}" }
                    val finalError = if (errorMessage.contains("Captcha session expired", ignoreCase = true)) {
                        context.getString(ru.protonmod.next.R.string.error_captcha_expired)
                    } else errorMessage
                    return@withContext Result.failure(Exception(finalError))
                }

                val finalAccessToken = nativeResult.accessToken
                val finalRefreshToken = nativeResult.refreshToken
                val finalUid = nativeResult.sessionId

                // If 2FA is not required, proceed to complete setup
                if (!nativeResult.scopes.contains("twofactor")) {
                    ProtonLogger.d(TAG, "[Login] Completing authentication. Registering VPN cert...")
                    val keys = registerAndGetVpnKeys(finalAccessToken, finalUid)

                    val vpnInfoResult = vpnRepository.getVpnInfo(finalAccessToken, finalUid)
                    val userTier = vpnInfoResult.getOrNull()?.vpnInfo?.maxTier ?: 0

                    saveSessionLocally(
                        accessToken = finalAccessToken,
                        refreshToken = finalRefreshToken,
                        sessionId = finalUid,
                        userId = nativeResult.userId,
                        userTier = userTier,
                        wgPrivateKey = keys.first.privateKeyX25519,
                        wgPublicKeyPem = keys.first.publicKeyPem,
                        wgCertificate = keys.third,
                        vpnIpv4 = keys.second.ipv4,
                        vpnIpv6 = keys.second.ipv6,
                        vpnDns = keys.second.dns?.joinToString(",")
                    )

                    vpnRepository.getServers(finalAccessToken, finalUid, userTier)
                }

                ProtonLogger.d(TAG, "[Login] Success. Scopes: ${nativeResult.scopes.joinToString()}")
                Result.success(LoginResponse(
                    code = 1000,
                    accessToken = finalAccessToken,
                    refreshToken = finalRefreshToken,
                    sessionId = finalUid,
                    userId = nativeResult.userId,
                    scopes = nativeResult.scopes.toList()
                ))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ProtonLogger.e(TAG, "[Login] Exception thrown", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Anonymous login flow (Guest login).
     */
    suspend fun loginAnonymous(captchaToken: String? = null): Result<LoginResponse> = authMutex.withLock {
        withContext(dispatcherProvider.io() + authJob) {
            try {
                val tokenType = if (captchaToken != null) "captcha" else null
                val challengePayload = pendingChallengePayload ?: buildChallengePayload().also { pendingChallengePayload = it }

                ProtonLogger.d(TAG, "[AnonymousLogin] Starting flow. Have Captcha: ${captchaToken != null}")

                var usedTokenInPhase0 = false
                if (pendingAnonToken == null) {
                    ProtonLogger.d(TAG, "[AnonymousLogin] Requesting initial anonymous session")
                    val anonSession = authApi.createAnonymousSession(challengePayload, captchaToken, tokenType)
                    pendingAnonToken = anonSession.accessToken
                    pendingAnonUid = anonSession.sessionId
                    if (captchaToken != null) usedTokenInPhase0 = true
                }

                val anonToken = pendingAnonToken ?: throw Exception("Failed to get anonymous session")
                val anonUid = pendingAnonUid ?: throw Exception("Failed to get anonymous UID")
                val bearer = "Bearer $anonToken"

                // Ensure token is not sent twice
                val phase1Token = if (usedTokenInPhase0) null else captchaToken
                val phase1TokenType = if (phase1Token != null) "captcha" else null

                ProtonLogger.d(TAG, "[AnonymousLogin] Upgrading to credentialless session using UID: $anonUid")
                val response = authApi.performLoginLess(bearer, anonUid, challengePayload, phase1Token, phase1TokenType)

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
                        wgPrivateKey = keys.first.privateKeyX25519,
                        wgPublicKeyPem = keys.first.publicKeyPem,
                        wgCertificate = keys.third,
                        vpnIpv4 = keys.second.ipv4,
                        vpnIpv6 = keys.second.ipv6,
                        vpnDns = keys.second.dns?.joinToString(",")
                    )

                    vpnRepository.getServers(finalAccessToken, finalUid, 0)
                    Result.success(response.copy(accessToken = finalAccessToken, sessionId = finalUid))
                } else {
                    Result.failure(Exception("Guest login failed: Code ${response.code}"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                if (e is SocketTimeoutException || e is ConnectException) {
                    ProtonLogger.w(TAG, "[AnonymousLogin] Network timeout error: ${e.message}")
                    clearPendingAuth()
                    return@withContext Result.failure(e)
                }

                if (e !is HttpException) ProtonLogger.e(TAG, "[AnonymousLogin] Exception thrown", e)
                handleHttpError(e)
            }
        }
    }

    suspend fun refreshSession(sessionId: String, refreshToken: String): Result<LoginResponse> = withContext(dispatcherProvider.io()) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            return@withContext Result.failure(Exception("Debounced"))
        }

        try {
            val refreshRequest = RefreshSessionRequest(uid = sessionId, refreshToken = refreshToken)
            val refreshResponse = authApi.refreshSession(refreshRequest)

            if (refreshResponse.code == 1000 && refreshResponse.accessToken != null) {
                lastRefreshTime = System.currentTimeMillis()

                val currentSession = sessionDao.getSession()
                if (currentSession != null && currentSession.sessionId == sessionId) {
                    val updatedSession = currentSession.copy(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken ?: currentSession.refreshToken
                    )
                    sessionDao.saveSession(updatedSession)
                }
                Result.success(refreshResponse)
            } else {
                Result.failure(Exception("Refresh failed with code ${refreshResponse.code}"))
            }
        } catch (e: Exception) {
            if (e is HttpException && e.code() in FORCE_LOGOUT_HTTP_CODES) {
                logout()
            }
            Result.failure(e)
        }
    }

    suspend fun verify2FA(
        sessionId: String,
        tempAccessToken: String,
        refreshToken: String,
        totpCode: String
    ): Result<LoginResponse> = authMutex.withLock {
        withContext(dispatcherProvider.io() + authJob) {
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
                    wgPrivateKey = keys.first.privateKeyX25519,
                    wgPublicKeyPem = keys.first.publicKeyPem,
                    wgCertificate = keys.third,
                    vpnIpv4 = keys.second.ipv4,
                    vpnIpv6 = keys.second.ipv6,
                    vpnDns = keys.second.dns?.joinToString(",")
                )

                vpnRepository.getServers(fullToken, sessionId, userTier)
                Result.success(response2fa.copy(userId = finalUserId))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e !is HttpException) ProtonLogger.e(TAG, "[verify2FA] Exception thrown", e)
                handleHttpError(e)
            }
        }
    }

    suspend fun getAvailableDomains(type: String = "login"): Result<List<String>> = withContext(dispatcherProvider.io()) {
        try {
            val session = sessionDao.getSession()
            val authHeader = session?.accessToken?.let { "Bearer $it" }
            val response = authApi.getAvailableDomains(
                authorization = authHeader,
                sessionId = session?.sessionId,
                type = type
            )
            if (response.code == 1000) {
                Result.success(response.domains)
            } else {
                Result.failure(Exception("Failed to get available domains: Code ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildChallengePayload(): JsonObject {
        return buildJsonObject {
            putJsonObject("Payload") {
                putJsonObject("vpn-android-v4-challenge-0") {
                    put("type", "me.proton.core.challenge.data.frame.ChallengeFrame.Device")
                    put("v", DeviceInfoProvider.SPOOFED_APP_VERSION)
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

    private suspend fun registerAndGetVpnKeys(accessToken: String, sessionId: String): Triple<VpnKeyPair, CreateCertificateResponse, String> {
        try {
            val vpnKeyPair = cryptoWrapper.generateVpnKeyPair()
            val regResult = vpnRepository.registerWireGuardKey(accessToken, sessionId, vpnKeyPair.publicKeyPem)

            if (regResult.isSuccess) {
                val response = regResult.getOrNull()!!
                return Triple(vpnKeyPair, response, response.certificate ?: "")
            } else {
                throw Exception("WireGuard key registration failed: ${regResult.exceptionOrNull()?.message ?: "unknown error"}")
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun saveSessionLocally(
        accessToken: String, refreshToken: String, sessionId: String, userId: String,
        userTier: Int, wgPrivateKey: String?, wgPublicKeyPem: String?, wgCertificate: String?,
        vpnIpv4: String? = null, vpnIpv6: String? = null, vpnDns: String? = null
    ) {
        require(!wgPrivateKey.isNullOrEmpty()) { "Cannot save session without a VPN private key" }
        sessionDao.saveSession(
            SessionEntity(
                accessToken = accessToken, refreshToken = refreshToken, sessionId = sessionId,
                userId = userId, userTier = userTier, wgPrivateKey = wgPrivateKey,
                wgPublicKeyPem = wgPublicKeyPem, wgCertificate = wgCertificate,
                vpnIpv4 = vpnIpv4, vpnIpv6 = vpnIpv6, vpnDns = vpnDns
            )
        )
    }

    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val response = e.response()
            val errorBody = response?.errorBody()?.string()
            val code = e.code()

            // Try to capture session ID from headers if present
            val sessionHeader = response?.headers()?.get("X-PM-Session-ID")
                ?: response?.headers()?.get("x-pm-session-id")
            if (sessionHeader != null && pendingAnonUid == null) {
                pendingAnonUid = sessionHeader
            }

            val shouldClearAuth = (code == 401 || code == 403 || code == 422)

            if (code == 422 && errorBody != null) {
                try {
                    val parsedError = jsonParser.decodeFromString<ProtonErrorResponse>(errorBody)
                    // 9001 = Needs Captcha
                    if (parsedError.code == 9001) {
                        val url = parsedError.details?.webUrl ?: ""
                        val token = parsedError.details?.humanVerificationToken ?: ""
                        val pendingUid = getPendingUid()

                        // Failsafe: if we don't have a UID, we MUST NOT throw CaptchaRequiredException
                        // because WebView won't have x-pm-uid to bind the token properly.
                        if (pendingUid == null) {
                            clearPendingAuth()
                            return Result.failure(Exception("Missing session for CAPTCHA. Please try again."))
                        }

                        ProtonLogger.w(TAG, "CAPTCHA Verification Required. Token extracted. PendingUID: $pendingUid")
                        return Result.failure(CaptchaRequiredException(url, token, pendingUid))
                    }
                    // 12087 = Captcha validation failed due to payload mismatch or session reset.
                    if (parsedError.code == 12087) {
                        ProtonLogger.e(TAG, "Captcha validation failed (12087): session invalidated. Forcing fresh restart.")
                        clearPendingAuth()
                        // FIX: We must NOT return CaptchaRequiredException here. The session is dead.
                        // Returning a standard exception forces the UI to reset, and the user's next attempt
                        // will cleanly create a new session and hit 9001 again safely.
                        return Result.failure(Exception("Captcha session expired. Please click Login to try again."))
                    }
                } catch (ex: Exception) {
                    ProtonLogger.w(TAG, "Failed to parse 422 error body: ${ex.message}")
                }
            }

            if (shouldClearAuth) {
                clearPendingAuth()
            }

            if (code == 422 && errorBody != null) {
                return Result.failure(Exception("HTTP 422: $errorBody"))
            }
            return Result.failure(Exception("HTTP $code: ${errorBody ?: e.message()}"))
        }
        return Result.failure(e)
    }
}