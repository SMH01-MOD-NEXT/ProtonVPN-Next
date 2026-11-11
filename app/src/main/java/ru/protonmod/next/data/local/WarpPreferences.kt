/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.local

import kotlinx.serialization.Serializable
import ru.protonmod.next.data.network.WarpEndpoints

/** Non-secret, device-local settings, independent from Proton AWG parameters. */
@Serializable
data class WarpPreferences(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val customEndpoint: Boolean = false,
    val customHost: String = "",
    val port: Int = 2408,
    /** 0 = WireGuard; 1/2/3 = WARP #1/#2/#3. */
    val profile: Int = 2,
) {
    fun validateShape() {
        require(profile in 0..3 && port in WarpEndpoints.ports)
        require(endpoint.isEmpty() || endpoint in WarpEndpoints.allIps)
        require(customHost.length <= 253)
    }
    // The Android tunnel currently resolves IPv4 endpoints only.
    fun resolvedEndpoint(): String? = if (customEndpoint) WarpEndpoints.normalizeHost(customHost.trim()).also { require(it.isNotEmpty() && ':' !in it) }
        else endpoint.takeIf(String::isNotEmpty)
    fun validate() { validateShape(); WarpEndpoints.validate(resolvedEndpoint(), port, customEndpoint) }
}
