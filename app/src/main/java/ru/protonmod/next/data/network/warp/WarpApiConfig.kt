/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.warp

import kotlinx.serialization.json.*
import ru.protonmod.next.vpn.ImportedWireGuard

internal object WarpApiConfig {
    fun build(link: String, port: Int, user: String, password: String): String {
        require(port in 1..65535 && user.isNotBlank() && password.isNotBlank())
        val parsed = ImportedWireGuard.parse(ImportedWireGuard.decode(link), "warp-api")
        val endpoint = parsed.outbound
        val peer = endpoint.getValue("peers").jsonArray.single().jsonObject
        // Resolve the physical endpoint before entering this config. DNS for API
        // domains is inside WARP; it must never bootstrap the WARP peer itself.
        require(peer.getValue("address").jsonPrimitive.content.matches(Regex("[0-9]+(\\.[0-9]+){3}")))
        return buildJsonObject {
            putJsonObject("log") { put("disabled", true) }
            putJsonArray("inbounds") { add(buildJsonObject {
                put("type", "socks"); put("tag", "api-loopback"); put("listen", "127.0.0.1"); put("listen_port", port)
                putJsonArray("users") { add(buildJsonObject { put("username", user); put("password", password) }) }
            }) }
            put("endpoints", JsonArray(listOf(endpoint)))
            putJsonObject("route") { put("final", "warp-api"); put("auto_detect_interface", true)
                put("default_domain_resolver", "warp-dns") }
            putJsonObject("dns") {
                put("strategy", "ipv4_only"); put("final", "warp-dns")
                putJsonArray("servers") { add(buildJsonObject {
                    put("type", "udp"); put("tag", "warp-dns"); put("server", parsed.name); put("detour", "warp-api")
                }) }
            }
        }.toString()
    }
}
