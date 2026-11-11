/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network

import ru.protonmod.next.utils.crypto.QuicI1Generator
import ru.protonmod.next.vpn.ImportedWireGuard

/** Public candidates only; see docs/warp-endpoints.json for provenance and limitations. */
object WarpEndpoints {
    val officialPorts = listOf(2408, 500, 1701, 4500)
    val ports = listOf(500, 854, 859, 864, 878, 880, 890, 891, 894, 903, 908, 928, 934, 939, 942, 943, 945, 946, 955, 968, 987, 988, 1002, 1010, 1014, 1018, 1070, 1074, 1180, 1387, 1701, 1843, 2371, 2408, 2506, 3138, 3476, 3581, 3854, 4177, 4198, 4233, 4500, 5279, 5956, 7103, 7152, 7156, 7281, 7559, 8319, 8742, 8854, 8886)
    val communityPrefixes = listOf("162.159.192.", "162.159.195.", "8.6.112.", "8.34.70.", "8.34.146.", "8.35.211.", "8.39.125.", "8.39.204.", "8.39.214.", "8.47.69.", "188.114.96.", "188.114.97.", "188.114.98.", "188.114.99.")
    val officialIps = (0..255).map { "162.159.193.$it" }
    val communityIps = communityPrefixes.flatMap { prefix -> (1..10).map { "$prefix$it" } }
    val allIps = (officialIps + communityIps).distinct()
    val orderedPorts = officialPorts + ports.filterNot { it in officialPorts }
    fun normalizeHost(value: String): String {
        require(value.length <= 253 && value == value.trim() && value.none { it.isWhitespace() || it in "/@?#\\" })
        if (value.isEmpty()) return ""
        if (':' in value) {
            require(value.startsWith('[') == value.endsWith(']'))
            val raw = value.removePrefix("[").removeSuffix("]")
            require('%' !in raw && raw.all { it in "0123456789abcdefABCDEF:." })
            // Numeric-only IPv6: InetAddress cannot perform a hostname lookup here.
            require(java.net.InetAddress.getByName(raw) is java.net.Inet6Address)
            return "[$raw]"
        }
        if (value.all { it in '0'..'9' || it == '.' }) {
            val parts = value.split('.')
            require(parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.toInt() in 0..255 && (it.length == 1 || !it.startsWith('0')) })
        } else require(value.split('.').all { it.isNotEmpty() && it.length <= 63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' } })
        return value.lowercase(java.util.Locale.ROOT)
    }
    fun validate(endpoint: String?, port: Int?, customEndpoint: Boolean = false) {
        require(endpoint.isNullOrBlank() || if (customEndpoint) normalizeHost(endpoint).isNotEmpty() else endpoint in allIps)
        require(port == null || port in ports)
    }
    fun apply(text: String, endpoint: String? = null, port: Int? = null, awg2: Boolean = false, profile: Int = if (awg2) 2 else 0, customEndpoint: Boolean = false): String {
        require(profile in 0..3)
        validate(endpoint, port, customEndpoint)
        val regex = Regex("(?m)^Endpoint = (.+)$")
        val old = requireNotNull(regex.find(text)).groupValues[1]
        var result = regex.replace(text, "Endpoint = ${endpoint?.takeIf(String::isNotBlank)?.let(::normalizeHost) ?: old.substringBeforeLast(':')}:${port ?: old.substringAfterLast(':')}")
        if (profile != 0) {
            // Match the public reference profile: no Reserved, fixed standard WG headers.
            val packets = when (profile) {
                1 -> WarpPackets.QUIC
                3 -> WarpPackets.SIP
                else -> "I1 = ${QuicI1Generator.generateI1("www.cloudflare.com")}"
            }
            val params = "S1 = 0\nS2 = 0\nS3 = 0\nS4 = 0\nJc = 4\nJmin = 40\nJmax = 70\nH1 = 1\nH2 = 2\nH3 = 3\nH4 = 4\n${packets}\n\n"
            result = result.replace(Regex("(?m)^Reserved = .*\n?"), "").replace("[Peer]", params + "[Peer]")
        }
        ImportedWireGuard.encode(result)
        return result
    }
}
