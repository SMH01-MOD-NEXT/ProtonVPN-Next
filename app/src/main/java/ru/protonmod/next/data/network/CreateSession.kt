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

// Request body for creating a VPN session.
// Proton API v1/certificate requires "ClientPublicKey" instead of "ClientEphemeralKey"
@Serializable
data class CreateSessionRequest(
    @SerialName("LogicalID") val logicalId: String,
    @SerialName("ServerID") val serverId: String,
    @SerialName("Protocol") val protocol: String = "wireguard",
    @SerialName("ClientPublicKey") val clientPublicKey: String
)

// Response from the session creation containing assigned IPs and node IPs
@Serializable
data class CreateSessionResponse(
    @SerialName("Code") val code: Int,
    @SerialName("ServerIP") val serverIp: String? = null, // The specific Node IP to connect to
    @SerialName("ServerEphemeralKey") val serverEphemeralKey: String? = null, // Ephemeral public key
    @SerialName("VPN") val vpn: VpnSessionInfo? = null
)

@Serializable
data class VpnSessionInfo(
    @SerialName("IP") val assignedIp: String? = null,
    @SerialName("ServerNodeIP") val serverIp: String? = null,
    @SerialName("DNS") val dns: List<String> = emptyList()
)