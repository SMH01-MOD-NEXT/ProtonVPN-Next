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

// --- Server-Generated EC Key Models ---
// We request the server to generate an EC key for us. The server will implicitly
// authorize the X25519 (WireGuard) counterpart of this key on its backend.

@Serializable
data class ServerKeyResponse(
    @SerialName("Code") val code: Int,
    @SerialName("PrivateKey") val privateKey: String,
    @SerialName("PublicKey") val publicKey: String
)