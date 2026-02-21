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
import java.security.SecureRandom
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

    private fun generateRandomState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val authInfo = authApi.getAuthInfo(AuthInfoRequest(username), captchaToken)
            if (authInfo.code != 1000) return@withContext Result.failure(Exception("Auth info failed"))

            // SRP Proof Generation
            val auth = Srp.newAuth(
                4L,
                username,
                passwordRaw.toByteArray(),
                authInfo.salt ?: "",
                authInfo.modulus ?: "",
                authInfo.serverEphemeral ?: ""
            )
            val proofs = auth.generateProofs(2048L)

            val loginRequest = LoginRequest(
                username = username,
                clientEphemeral = Base64.encodeToString(proofs.clientEphemeral, Base64.NO_WRAP),
                clientProof = Base64.encodeToString(proofs.clientProof, Base64.NO_WRAP),
                srpSession = authInfo.srpSession ?: ""
            )

            val loginResponse = authApi.performLogin(loginRequest, captchaToken)
            Log.e(TAG, "Initial Login Response: $loginResponse")
            Result.success(loginResponse)
        } catch (e: Exception) {
            handleHttpError(e)
        }
    }

    /**
     * Complete 2FA flow: Verify -> Refresh -> Get User Info
     */
    suspend fun verify2FA(
        sessionId: String,
        tempAccessToken: String,
        refreshToken: String,
        totpCode: String
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val bearer = "Bearer $tempAccessToken"

            // 1. Verify TOTP code
            val response2fa = authApi.performSecondFactor(bearer, sessionId, SecondFactorRequest(totpCode))
            if (response2fa.code != 1000) return@withContext Result.failure(Exception("2FA rejected"))

            // 2. Refresh Session to get upgraded tokens (Matches Go logic)
            val refreshRequest = RefreshSessionRequest(
                sessionId = sessionId,
                refreshToken = refreshToken,
                accessToken = tempAccessToken,
                state = generateRandomState()
            )
            val refreshedSession = authApi.refreshSession(refreshRequest)
            if (refreshedSession.code != 1000) return@withContext Result.failure(Exception("Refresh failed"))

            // 3. Fetch User Profile to get the actual UserID
            val fullToken = refreshedSession.accessToken ?: ""
            val userResponse = authApi.getUser("Bearer $fullToken")

            // Return session with actual UserID merged in
            Result.success(refreshedSession.copy(userId = userResponse.user?.id))
        } catch (e: Exception) {
            handleHttpError(e)
        }
    }

    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP Error: $errorBody", e)
            if (e.code() == 422 && errorBody != null) {
                try {
                    val parsedError = jsonParser.decodeFromString<ProtonErrorResponse>(errorBody)
                    if (parsedError.code == 9001) return Result.failure(CaptchaRequiredException(parsedError.details?.webUrl ?: ""))
                } catch (ex: Exception) { /* ignore */ }
            }
        }
        return Result.failure(e)
    }
}