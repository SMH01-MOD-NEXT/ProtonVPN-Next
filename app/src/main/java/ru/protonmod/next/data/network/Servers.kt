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

@Serializable
data class LogicalServersResponse(
    @SerialName("Code") val code: Int,
    @SerialName("LogicalServers") val logicalServers: List<LogicalServer> = emptyList()
)

@Serializable
data class LogicalServer(
    @SerialName("ID") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Tier") val tier: Int,
    @SerialName("Features") val features: Int,
    @SerialName("EntryCountry") val entryCountry: String,
    @SerialName("ExitCountry") val exitCountry: String,
    @SerialName("City") val city: String,
    @SerialName("Servers") val servers: List<PhysicalServer> = emptyList(),
    // Added field for UI convenience, not directly from logicals API
    var averageLoad: Int = 0
)

@Serializable
data class PhysicalServer(
    @SerialName("ID") val id: String,
    @SerialName("ExitIP") val exitIp: String? = null,
    @SerialName("Domain") val domain: String,
    @SerialName("Status") val status: Int,
    @SerialName("X25519PublicKey") val wgPublicKey: String? = null,
    // Added field to store load fetched from vpn/v1/loads
    var load: Int = 0
)
