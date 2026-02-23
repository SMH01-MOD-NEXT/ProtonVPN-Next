package ru.protonmod.next.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ProtonVpnApi {

    @GET("vpn/v2/logicals")
    suspend fun getLogicalServers(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Query("WithState") withState: Boolean = true
    ): LogicalServersResponse

    /**
     * Registers the WireGuard public key and obtains the internal VPN IP.
     * Based on Linux client: uses /vpn/v1/certificate
     */
    @POST("vpn/v1/certificate")
    suspend fun registerVpnKey(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Body request: CreateCertificateRequest
    ): CreateCertificateResponse
}