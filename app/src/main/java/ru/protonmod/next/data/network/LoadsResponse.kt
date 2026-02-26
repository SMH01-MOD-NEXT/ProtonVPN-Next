/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoadsResponse(
    @SerialName("Code") val code: Int? = null,
    @SerialName("LogicalServers") val loads: List<ServerLoad> = emptyList()
)

@Serializable
data class ServerLoad(
    @SerialName("ID") val id: String, // Logical or physical server ID
    @SerialName("Load") val load: Int, // Load in percent (0-100)
    @SerialName("Status") val status: Int? = null
)
