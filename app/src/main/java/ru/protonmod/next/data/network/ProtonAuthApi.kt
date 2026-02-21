package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// --- Auth Requests ---

@Serializable
data class AuthInfoRequest(
    @SerialName("Username") val username: String
)

@Serializable
data class LoginRequest(
    @SerialName("Username") val username: String,
    @SerialName("ClientEphemeral") val clientEphemeral: String,
    @SerialName("ClientProof") val clientProof: String,
    @SerialName("SRPSession") val srpSession: String,
    @SerialName("Payload") val payload: Map<String, String> = emptyMap()
)

@Serializable
data class SecondFactorRequest(
    @SerialName("TwoFactorCode") val twoFactorCode: String
)

/**
 * Matches AuthRefreshReq from go-proton-api
 */
@Serializable
data class RefreshSessionRequest(
    @SerialName("UID") val sessionId: String,
    @SerialName("RefreshToken") val refreshToken: String,
    @SerialName("ResponseType") val responseType: String = "token",
    @SerialName("GrantType") val grantType: String = "refresh_token",
    @SerialName("RedirectURI") val redirectUri: String = "https://protonmail.ch",
    @SerialName("State") val state: String,
    @SerialName("AccessToken") val accessToken: String? = null
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

// --- Retrofit Interface ---

interface ProtonAuthApi {

    @POST("auth/v4/info")
    suspend fun getAuthInfo(
        @Body request: AuthInfoRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): AuthInfoResponse

    @POST("auth/v4")
    suspend fun performLogin(
        @Body request: LoginRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): LoginResponse

    @POST("auth/v4/2fa")
    suspend fun performSecondFactor(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: SecondFactorRequest
    ): LoginResponse

    @POST("auth/v4/refresh")
    suspend fun refreshSession(
        @Body request: RefreshSessionRequest
    ): LoginResponse

    @GET("core/v4/user")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): UserResponse
}