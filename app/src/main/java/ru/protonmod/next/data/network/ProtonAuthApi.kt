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

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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

interface ProtonAuthApi {

    @POST("auth/v4/sessions")
    suspend fun createAnonymousSession(
        @Body payload: JsonObject,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): LoginResponse

    @POST("auth/v4/info")
    suspend fun getAuthInfo(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: AuthInfoRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): AuthInfoResponse

    @POST("auth/v4")
    suspend fun performLogin(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: LoginRequest,
        @Header("x-pm-human-verification-token") captchaToken: String? = null
    ): LoginResponse

    @POST("auth/v4/2fa")
    suspend fun performSecondFactor(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: SecondFactorRequest
    ): LoginResponse

    @GET("core/v4/users")
    suspend fun getUser(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String
    ): UserResponse
}
