package ru.protonmod.next.data.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Interface for specific VPN operations (Server lists, certificates, etc.)
 */
interface ProtonVpnApi {

    @GET("vpn/v2/logicals")
    suspend fun getLogicalServers(
        @Header("Authorization") authorization: String,
        @Header("x-pm-uid") sessionId: String,
        @Query("WithEntriesForProtocols") protocols: String = "WireGuardUDP,WireGuardTCP,OpenVPNUDP,OpenVPNTCP,WireGuardTLS",
        @Query("WithState") withState: Boolean = true
    ): LogicalServersResponse

    // TODO: В будущем сюда добавим POST "vpn/v1/certificate" для обмена ключами WG
}