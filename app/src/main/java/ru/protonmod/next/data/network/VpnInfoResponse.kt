/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VpnInfoResponse(
    @SerialName("Code") val code: Int,
    @SerialName("VPN") val vpnInfo: VpnInfo? = null,
    @SerialName("Subscribed") val subscribed: Int? = null,
    @SerialName("Services") val services: Int? = null,
    @SerialName("Delinquent") val delinquent: Int? = null,
    @SerialName("Credit") val credit: Int? = null
)

@Serializable
data class VpnInfo(
    @SerialName("Status") val status: Int? = null,
    @SerialName("ExpirationTime") val expirationTime: Long? = null,
    @SerialName("MaxTier") val maxTier: Int? = null,
    @SerialName("MaxConnect") val maxConnect: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Password") val password: String? = null,
    @SerialName("PlanName") val planName: String? = null,
    @SerialName("PlanDisplayName") val planDisplayName: String? = null,
    @SerialName("GroupID") val groupId: String? = null
)
