/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VpnUserResponse(
    @SerialName("Code") val code: Int,
    @SerialName("VPN") val vpnInfo: VpnUserInfo,
    @SerialName("Subscribed") val subscribed: Int,
    @SerialName("Services") val services: Int,
    @SerialName("Delinquent") val delinquent: Int
)

@Serializable
data class VpnUserInfo(
    @SerialName("Status") val status: Int,
    @SerialName("ExpirationTime") val expirationTime: Long,
    @SerialName("Tier") val maxTier: Int,
    @SerialName("MaxConnect") val maxConnect: Int,
    @SerialName("Name") val name: String,
    @SerialName("Password") val password: String,
    @SerialName("PlanName") val planName: String? = null
)
