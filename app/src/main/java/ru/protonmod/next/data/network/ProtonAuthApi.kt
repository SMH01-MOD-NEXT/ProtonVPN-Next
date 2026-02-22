package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// --- Auth Requests ---

@Serializable
data class AuthInfoRequest(
    @SerialName("Username") val username: String,
    @SerialName("Intent") val intent: String = "Auto"
)

@Serializable
data class LoginRequest(
    @SerialName("Username") val username: String,
    @SerialName("ClientEphemeral") val clientEphemeral: String,
    @SerialName("ClientProof") val clientProof: String,
    @SerialName("SRPSession") val srpSession: String,
    @SerialName("Payload") val payload: JsonObject? = null
)

@Serializable
data class SecondFactorRequest(
    @SerialName("TwoFactorCode") val twoFactorCode: String
)

// --- Responses ---

@Serializable
data class AuthInfoResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Modulus") val modulus: String? = null,
    @SerialName("ServerEphemeral") val serverEphemeral: String? = null,
    @SerialName("Salt") val salt: String? = null,
    @SerialName("SRPSession") val srpSession: String? = null
)

@Serializable
data class LoginResponse(
    @SerialName("Code") val code: Int,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("RefreshToken") val refreshToken: String? = null,
    @SerialName("UID") val sessionId: String? = null,
    @SerialName("UserID") val userId: String? = null,
    @SerialName("Scopes") val scopes: List<String> = emptyList()
)

@Serializable
data class UserResponse(
    @SerialName("Code") val code: Int,
    @SerialName("User") val user: UserInfo? = null
)

@Serializable
data class UserInfo(
    @SerialName("ID") val id: String,
    @SerialName("Name") val name: String
)

@Serializable
data class GenericResponse(
    @SerialName("Code") val code: Int
)

// --- Retrofit Interface ---

interface ProtonAuthApi {

    // 0. Создание обязательной анонимной сессии для VPN
    @POST("auth/v4/sessions")
    suspend fun createAnonymousSession(
        @Body payload: JsonObject,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): LoginResponse

    // 1. Получение инфо (ТЕПЕРЬ С ТОКЕНАМИ АНОНИМНОЙ СЕССИИ!)
    @POST("auth/v4/info")
    suspend fun getAuthInfo(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: AuthInfoRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): AuthInfoResponse

    // 2. Сам логин
    @POST("auth/v4")
    suspend fun performLogin(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: LoginRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): LoginResponse

    // 3. 2FA
    @POST("auth/v4/2fa")
    suspend fun performSecondFactor(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: SecondFactorRequest
    ): LoginResponse

    @DELETE("auth/v4")
    suspend fun revokeSession(
        @Query("AuthDevice") revokeAuthDevice: Int = 1
    ): GenericResponse

    @GET("core/v4/users")
    suspend fun getUser(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String
    ): UserResponse
}