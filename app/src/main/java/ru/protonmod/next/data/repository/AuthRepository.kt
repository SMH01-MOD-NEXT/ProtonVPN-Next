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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: ProtonAuthApi,
    private val sessionDao: SessionDao
) {
    companion object {
        private const val TAG = "AuthRepository"
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    suspend fun login(username: String, passwordRaw: String, captchaToken: String? = null): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[Login] Phase 0: Creating Anonymous Session")

            // 1. Собираем Anti-fraud payload
            val challengePayload = buildJsonObject {
                putJsonObject("Payload") {
                    putJsonObject("vpn-android-v4-challenge-0") {
                        put("v", "2.0.7")
                        put("appLang", "ru")
                        put("timezone", "Europe/Volgograd")
                        put("deviceName", 53319294142L)
                        put("regionCode", "KZ")
                        put("timezoneOffset", -180)
                        put("isJailbreak", false)
                        put("preferredContentSize", "1.0")
                        put("storageCapacity", 256.0)
                        put("isDarkmodeOn", true)
                        putJsonArray("keyboards") {
                            add("com.huawei.ohos.inputmethod")
                            add("com.google.android.googlequicksearchbox")
                            add("org.futo.inputmethod.latin")
                            add("com.touchtype.swiftkey")
                        }
                    }
                }
            }

            // 2. Получаем анонимные токены
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

            // Если 2FA не нужна, сохраняем сессию
            if (!loginResponse.scopes.contains("twofactor")) {
                saveSessionLocally(finalAccessToken, finalRefreshToken, finalUid, loginResponse.userId ?: "")
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

            saveSessionLocally(fullToken, refreshToken, sessionId, finalUserId)

            Log.d(TAG, "[2FA] Complete. Final UserID: $finalUserId")
            Result.success(response2fa.copy(userId = finalUserId))
        } catch (e: Exception) {
            Log.e(TAG, "[2FA] Critical failure in 2FA flow", e)
            handleHttpError(e)
        }
    }

    private suspend fun saveSessionLocally(accessToken: String, refreshToken: String, sessionId: String, userId: String) {
        sessionDao.saveSession(
            SessionEntity(
                accessToken = accessToken,
                refreshToken = refreshToken,
                sessionId = sessionId,
                userId = userId
            )
        )
    }

    private fun handleHttpError(e: Exception): Result<LoginResponse> {
        if (e is HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val url = e.response()?.raw()?.request()?.url()
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