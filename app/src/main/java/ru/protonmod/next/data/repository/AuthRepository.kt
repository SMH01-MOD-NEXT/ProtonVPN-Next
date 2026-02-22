package ru.protonmod.next.data.repository

import android.util.Base64
import android.util.Log
import com.proton.gopenpgp.srp.Srp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.protonmod.next.data.network.*
import ru.protonmod.next.ui.screens.CaptchaRequiredException
import ru.protonmod.next.ui.screens.ProtonErrorResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: ProtonAuthApi
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    /**
     * Initial login attempt using SRP (Secure Remote Password) protocol
     */
    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[Login] Phase 1: Requesting Auth Info")
            val authInfo = authApi.getAuthInfo(AuthInfoRequest(username), captchaToken)
            if (authInfo.code != 1000) return@withContext Result.failure(Exception("Auth info failed: ${authInfo.code}"))

            // SRP Proof Generation using gopenpgp
            val auth = Srp.newAuth(4L, username, passwordRaw.toByteArray(), authInfo.salt ?: "", authInfo.modulus ?: "", authInfo.serverEphemeral ?: "")
            val proofs = auth.generateProofs(2048L)

            val loginRequest = LoginRequest(
                username = username,
                clientEphemeral = Base64.encodeToString(proofs.clientEphemeral, Base64.NO_WRAP),
                clientProof = Base64.encodeToString(proofs.clientProof, Base64.NO_WRAP),
                srpSession = authInfo.srpSession ?: ""
            )

            Log.d(TAG, "[Login] Phase 2: Performing Login SRP")
            val loginResponse = authApi.performLogin(loginRequest, captchaToken)
            Log.d(TAG, "[Login] Success. Scopes: ${loginResponse.scopes}")
            Result.success(loginResponse)
        } catch (e: Exception) {
            handleHttpError(e)
        }
    }

    /**
     * Complete 2FA flow for Proton VPN: Verify TOTP -> Get Final User Data
     * Note: VPN API does not require a /refresh call after successful 2FA.
     */
    suspend fun verify2FA(
        sessionId: String,
        tempAccessToken: String,
        totpCode: String
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $tempAccessToken"

            // Step 1: Verify TOTP directly
            Log.d(TAG, "[2FA] Step 1: Submitting TOTP to auth/v4/2fa")
            val response2fa = authApi.performSecondFactor(bearer, sessionId, SecondFactorRequest(totpCode))

            if (response2fa.code != 1000) {
                Log.e(TAG, "[2FA] Verification failed with code: ${response2fa.code}")
                return@withContext Result.failure(Exception("2FA rejected: ${response2fa.code}"))
            }

            // The session is now fully upgraded on the backend.
            // As seen in your logs, the response doesn't contain a new token, so we keep using the temp one
            val fullToken = response2fa.accessToken ?: tempAccessToken
            val fullBearer = "Bearer $fullToken"

            // Step 2: Fetch Profile to get actual User ID
            Log.d(TAG, "[2FA] Step 2: Getting User ID at core/v4/users")
            // WE NOW PASS THE SESSION ID HERE TO PREVENT 404
            val userResponse = authApi.getUser(fullBearer, sessionId)

            Log.d(TAG, "[2FA] Complete. Final UserID: ${userResponse.user?.id}")

            // Return the successful 2FA response, mixed with the fetched User ID
            Result.success(response2fa.copy(userId = userResponse.user?.id))
        } catch (e: Exception) {
            Log.e(TAG, "[2FA] Critical failure in 2FA flow", e)
            handleHttpError(e)
        }
    }

    /**
     * Centralized error handling for Proton-specific HTTP errors (like Captcha)
     */
    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            // Use explicit method calls request() and url() to avoid visibility errors
            val url = e.response()?.raw()?.request()?.url()
            Log.e(TAG, "HTTP Error ${e.code()} at URL: $url")
            Log.e(TAG, "Error body: $errorBody")

            if (e.code() == 422 && errorBody != null) {
                try {
                    val parsedError = jsonParser.decodeFromString<ProtonErrorResponse>(errorBody)
                    // Code 9001 indicates Human Verification (Captcha) is required
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