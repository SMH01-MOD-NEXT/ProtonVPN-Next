/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Parses share links into trusted sing-box outbound objects. Arbitrary JSON is never accepted. */
object ProxyLinkParser {
    data class ParsedProxy(val name: String, val outbound: JsonObject)
    data class ProxyLinkInfo(
        val name: String,
        val protocol: String,
        val server: String,
        val port: Int
    )

    fun inspectLink(link: String): ProxyLinkInfo {
        val normalized = link.trim()
        require(normalized.isNotBlank() && '\n' !in normalized && '\r' !in normalized) {
            "Expected exactly one proxy link"
        }
        val parsed = parse(normalized, "preview")
        if (parsed.outbound["type"]?.jsonPrimitive?.content in setOf("awg", "wireguard")) {
            val peer = parsed.outbound.getValue("peers").let { it as JsonArray }.first().jsonObject
            return ProxyLinkInfo("WireGuard", parsed.outbound.getValue("type").jsonPrimitive.content, peer.getValue("address").jsonPrimitive.content, peer.getValue("port").jsonPrimitive.content.toInt())
        }
        return ProxyLinkInfo(
            name = parsed.name,
            protocol = parsed.outbound.getValue("type").jsonPrimitive.content,
            server = parsed.outbound.getValue("server").jsonPrimitive.content,
            port = parsed.outbound.getValue("server_port").jsonPrimitive.content.toInt()
        )
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun parseChain(config: String): List<ParsedProxy> {
        val links = config.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(links.isNotEmpty()) { "Proxy chain is empty" }
        require(links.size <= 4) { "A proxy chain is limited to four hops" }
        return links.mapIndexed { index, link -> parse(link, "proxy-${index + 1}") }
            .mapIndexed { index, proxy ->
                val nextTag = if (index < links.lastIndex) "proxy-${index + 2}" else null
                if (nextTag == null) proxy else proxy.copy(
                    outbound = JsonObject(proxy.outbound + ("detour" to JsonPrimitive(nextTag)))
                )
            }
    }

    fun isValid(config: String): Boolean = runCatching { parseChain(config) }.isSuccess

    private fun parse(link: String, tag: String): ParsedProxy = when {
        link.startsWith(ImportedWireGuard.SCHEME) -> ImportedWireGuard.parse(ImportedWireGuard.decode(link), tag)
        link.startsWith("vless://", ignoreCase = true) -> parseVless(link, tag)
        link.startsWith("vmess://", ignoreCase = true) -> parseVmess(link, tag)
        else -> throw IllegalArgumentException("Only vless:// and vmess:// links are supported")
    }

    private fun parseVless(link: String, tag: String): ParsedProxy {
        val uri = URI(link)
        val id = uri.userInfo?.substringBefore(':').orEmpty()
        require(uuid.matches(id)) { "Invalid VLESS UUID" }
        val host = uri.host.orEmpty()
        require(host.isNotBlank()) { "VLESS server is missing" }
        val port = uri.port
        require(port in 1..65535) { "Invalid VLESS port" }
        val query = parseQuery(uri.rawQuery)
        require(query["encryption"].isNullOrBlank() || query["encryption"] == "none") {
            "Unsupported VLESS encryption"
        }

        val values = linkedMapOf<String, JsonElement>(
            "type" to JsonPrimitive("vless"),
            "tag" to JsonPrimitive(tag),
            "server" to JsonPrimitive(host),
            "server_port" to JsonPrimitive(port),
            "uuid" to JsonPrimitive(id),
            "packet_encoding" to JsonPrimitive(query["packetEncoding"] ?: "xudp")
        )
        query["flow"]?.takeIf(String::isNotBlank)?.let { values["flow"] = JsonPrimitive(it) }
        addTls(values, query, host)
        addTransport(values, query)
        addDomainResolver(values, host)
        return ParsedProxy(decode(uri.rawFragment).ifBlank { "VLESS" }, JsonObject(values))
    }

    private fun parseVmess(link: String, tag: String): ParsedProxy {
        val encoded = link.removePrefix("vmess://").substringBefore('#').trim()
        val decoded = decodeBase64(encoded).toString(StandardCharsets.UTF_8)
        val source = json.parseToJsonElement(decoded).jsonObject
        fun string(name: String) = source[name]?.jsonPrimitive?.content.orEmpty()

        val host = string("add")
        val port = string("port").toIntOrNull()
        val id = string("id")
        require(host.isNotBlank()) { "VMess server is missing" }
        require(port != null && port in 1..65535) { "Invalid VMess port" }
        require(uuid.matches(id)) { "Invalid VMess UUID" }

        val transportType = string("net").ifBlank { "tcp" }
        val query = mutableMapOf(
            "type" to transportType,
            "host" to string("host"),
            "path" to string("path"),
            "serviceName" to string("path"),
            "security" to string("tls"),
            "sni" to string("sni"),
            "fp" to string("fp"),
            "alpn" to string("alpn")
        )
        val values = linkedMapOf<String, JsonElement>(
            "type" to JsonPrimitive("vmess"),
            "tag" to JsonPrimitive(tag),
            "server" to JsonPrimitive(host),
            "server_port" to JsonPrimitive(port),
            "uuid" to JsonPrimitive(id),
            "security" to JsonPrimitive(string("scy").ifBlank { "auto" }),
            "alter_id" to JsonPrimitive(string("aid").toIntOrNull() ?: 0),
            "packet_encoding" to JsonPrimitive("xudp")
        )
        addTls(values, query, host)
        addTransport(values, query)
        addDomainResolver(values, host)
        return ParsedProxy(string("ps").ifBlank { "VMess" }, JsonObject(values))
    }

    private fun addTransport(values: MutableMap<String, JsonElement>, query: Map<String, String>,) {
        when (val type = query["type"].orEmpty().ifBlank { "tcp" }.lowercase()) {
            // Xray share links use both `tcp` and the newer `raw` name for
            // the same unframed stream transport. sing-box represents both by
            // omitting the transport object.
            "tcp", "none", "raw" -> Unit
            "ws" -> {
                val transport = linkedMapOf<String, JsonElement>(
                    "type" to JsonPrimitive("ws"),
                    "path" to JsonPrimitive(query["path"].orEmpty().ifBlank { "/" })
                )
                query["host"]?.takeIf(String::isNotBlank)?.let {
                    transport["headers"] = JsonObject(mapOf("Host" to JsonPrimitive(it)))
                }
                values["transport"] = JsonObject(transport)
            }
            "httpupgrade" -> values["transport"] = JsonObject(buildMap {
                put("type", JsonPrimitive("httpupgrade"))
                put("path", JsonPrimitive(query["path"].orEmpty().ifBlank { "/" }))
                query["host"]?.takeIf(String::isNotBlank)?.let { put("host", JsonPrimitive(it)) }
            })
            else -> throw IllegalArgumentException("Unsupported transport: $type")
        }
    }

    private fun addTls(values: MutableMap<String, JsonElement>, query: Map<String, String>, host: String) {
        val security = query["security"].orEmpty().lowercase()
        if (security != "tls" && security != "reality") return
        val tls = linkedMapOf<String, JsonElement>(
            "enabled" to JsonPrimitive(true),
            "server_name" to JsonPrimitive(query["sni"].orEmpty().ifBlank { host })
        )
        query["alpn"]?.takeIf(String::isNotBlank)?.let {
            tls["alpn"] = JsonArray(it.split(',').map(String::trim).filter(String::isNotEmpty).map(::JsonPrimitive))
        }
        query["fp"]?.takeIf(String::isNotBlank)?.let {
            tls["utls"] = JsonObject(mapOf("enabled" to JsonPrimitive(true), "fingerprint" to JsonPrimitive(it)))
        }
        if (security == "reality") {
            val key = query["pbk"].orEmpty()
            require(key.isNotBlank()) { "Reality public key is missing" }
            tls["reality"] = JsonObject(buildMap {
                put("enabled", JsonPrimitive(true))
                put("public_key", JsonPrimitive(key))
                query["sid"]?.takeIf(String::isNotBlank)?.let { put("short_id", JsonPrimitive(it)) }
            })
        }
        values["tls"] = JsonObject(tls)
    }

    private fun addDomainResolver(values: MutableMap<String, JsonElement>, host: String) {
        if (host.any(Char::isLetter)) values["domain_resolver"] = JsonPrimitive("bootstrap-dns")
    }

    private fun parseQuery(raw: String?): Map<String, String> = raw.orEmpty().split('&')
        .filter(String::isNotBlank)
        .associate { part -> decode(part.substringBefore('=')) to decode(part.substringAfter('=', "")) }

    private fun decode(value: String?): String = URLDecoder.decode(value.orEmpty(), StandardCharsets.UTF_8.name())

    private fun decodeBase64(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.getDecoder().decode(padded)
    }
}
