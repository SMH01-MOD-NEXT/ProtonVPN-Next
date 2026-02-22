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
    @SerialName("Name") val name: String, // e.g. "NL-FREE#1"
    @SerialName("Tier") val tier: Int, // 0 = Free, 1 = Basic, 2 = Plus
    @SerialName("Features") val features: Int,
    @SerialName("EntryCountry") val entryCountry: String,
    @SerialName("ExitCountry") val exitCountry: String,
    @SerialName("City") val city: String,
    @SerialName("Servers") val servers: List<PhysicalServer> = emptyList()
)

@Serializable
data class PhysicalServer(
    @SerialName("ID") val id: String,
    @SerialName("ExitIP") val exitIp: String? = null,
    @SerialName("Domain") val domain: String,
    @SerialName("Status") val status: Int,
    @SerialName("X25519PublicKey") val wgPublicKey: String? = null
)