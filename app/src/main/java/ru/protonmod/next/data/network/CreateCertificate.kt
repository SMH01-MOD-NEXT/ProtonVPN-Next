package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateCertificateRequest(
    @SerialName("ClientPublicKey") val clientPublicKey: String
)

@Serializable
data class CreateCertificateResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Certificate") val certificate: String? = null // Often empty for WG, used for OpenVPN
)