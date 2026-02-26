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