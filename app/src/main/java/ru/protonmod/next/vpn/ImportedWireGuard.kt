/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.vpn

import kotlinx.serialization.json.*
import java.net.URI
import java.util.Base64

/** Strict wg-quick subset: shell hooks and unknown directives are rejected, never executed. */
object ImportedWireGuard {
    const val SCHEME = "nextwg://"
    fun encode(text: String): String {
        parse(text, "validate")
        return SCHEME + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    }
    fun decode(link: String): String = String(Base64.getUrlDecoder().decode(link.removePrefix(SCHEME)))
    fun parse(text: String, tag: String): ProxyLinkParser.ParsedProxy {
        require(text.length <= 65536)
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var current = ""
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith('[')) {
                require(line in listOf("[Interface]", "[Peer]"))
                current = line; require(current !in sections); sections[current] = mutableMapOf()
            } else {
                require(current.isNotEmpty() && '=' in line)
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                require(sections.getValue(current).put(key, value) == null)
            }
        }
        val iface = sections.getValue("[Interface]"); val peer = sections.getValue("[Peer]")
        val awgKeys = setOf("Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "I2", "I3", "I4", "I5")
        require(iface.keys.all { it in setOf("PrivateKey", "Address", "DNS", "MTU") + awgKeys })
        require(peer.keys.all { it in setOf("PublicKey", "PresharedKey", "Endpoint", "AllowedIPs", "PersistentKeepalive", "Reserved") })
        fun key(value: String?): String = requireNotNull(value).also { require(Base64.getDecoder().decode(it).size == 32) }
        fun list(value: String?) = value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        val addresses = list(iface["Address"])
        require(addresses.isNotEmpty() && addresses.all(::cidr))
        val allowed = list(peer["AllowedIPs"])
        require(allowed.isNotEmpty() && allowed.all(::cidr))
        val uri = URI("udp://${peer.getValue("Endpoint")}")
        require(!uri.host.isNullOrBlank() && uri.port in 1..65535 && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.isNullOrEmpty())
        val mtu = iface["MTU"]?.toInt() ?: 1280; require(mtu in 576..9000)
        val keepalive = peer["PersistentKeepalive"]?.toInt() ?: 25; require(keepalive in 0..65535)
        val dns = list(iface["DNS"]).firstOrNull() ?: "1.1.1.1"
        require(numericIp(dns))
        val isAwg = iface.keys.any { it in awgKeys }
        require(!isAwg || "Reserved" !in peer)
        val peerJson = buildJsonObject {
            put("address", uri.host.removeSurrounding("[", "]")); put("port", uri.port)
            put("public_key", key(peer["PublicKey"]))
            put("allowed_ips", JsonArray(allowed.map(::JsonPrimitive)))
            put("persistent_keepalive_interval", keepalive)
            peer["PresharedKey"]?.let { put(if (isAwg) "preshared_key" else "pre_shared_key", key(it)) }
            peer["Reserved"]?.let { raw ->
                val bytes = list(raw).map(String::toInt); require(bytes.size == 3 && bytes.all { it in 0..255 })
                put("reserved", JsonArray(bytes.map(::JsonPrimitive)))
            }
        }
        val endpoint = buildJsonObject {
            put("type", if (isAwg) "awg" else "wireguard"); put("tag", tag)
            if (isAwg) put("useIntegratedTun", false)
            put("private_key", key(iface["PrivateKey"])); put("mtu", mtu)
            put("address", JsonArray(addresses.map(::JsonPrimitive))); put("peers", JsonArray(listOf(peerJson)))
            awgKeys.forEach { k -> iface[k]?.let { v ->
                if (k.startsWith("J") || k.startsWith("S")) { val n = v.toInt(); require(n >= 0); put(k.lowercase(), n) }
                else put(k.lowercase(), v)
            } }
        }
        return ProxyLinkParser.ParsedProxy(dns, endpoint)
    }
    private fun numericIp(value: String): Boolean = when {
        ':' in value -> value.matches(Regex("[0-9a-fA-F:]+")) && runCatching { java.net.InetAddress.getByName(value) }.isSuccess
        else -> value.split('.').let { it.size == 4 && it.all { n -> n.toIntOrNull() in 0..255 } }
    }
    private fun cidr(value: String): Boolean {
        val ip = value.substringBefore('/'); val bits = value.substringAfter('/', "").toIntOrNull() ?: return false
        return numericIp(ip) && bits in 0..(if (':' in ip) 128 else 32)
    }
}
