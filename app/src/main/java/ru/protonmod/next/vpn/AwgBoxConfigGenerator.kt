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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.protonmod.next.netshield.NetShieldRuleSet
import javax.inject.Inject
import javax.inject.Singleton

data class MultiHopEndpoint(
    val publicKey: String,
    val targetIp: String,
    val port: Int
)

/** Builds an amnezia-box/sing-box configuration instead of a wg-quick config. */
interface AwgBoxConfigGenerator {
    fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean = false,
        allowLan: Boolean = false,
        selectedApps: Set<String> = emptySet(),
        selectedIps: Set<String> = emptySet(),
        selectedDomains: Set<String> = emptySet(),
        port: Int = 1194,
        certificate: String? = null,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams,
        proxyChainConfig: String? = null,
        netShieldRuleSets: List<NetShieldRuleSet> = emptyList(),
        proxyServerOverrides: Map<String, String> = emptyMap(),
        torModeEnabled: Boolean = false,
        torDataDirectory: String? = null,
        torExecutablePath: String? = null,
        multiHopEntry: MultiHopEndpoint? = null
    ): String
}

@Singleton
class AwgBoxConfigGeneratorImpl @Inject constructor(
    private val ipSubnetCalculator: IpSubnetCalculator
) : AwgBoxConfigGenerator {
    private companion object {
        const val MULTI_HOP_EXIT_WIREGUARD_PORT = 51820

        val IPV4_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
        const val TOR_FALLBACK_DNS = "1.1.1.1"
        const val TOR_VIRTUAL_ADDR_RANGE = "198.18.0.0/15"
        const val TOR_DNS_PORT = 19053
    }

    private val json = Json { prettyPrint = true; encodeDefaults = false }

    override fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean,
        allowLan: Boolean,
        selectedApps: Set<String>,
        selectedIps: Set<String>,
        selectedDomains: Set<String>,
        port: Int,
        certificate: String?,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams,
        proxyChainConfig: String?,
        netShieldRuleSets: List<NetShieldRuleSet>,
        proxyServerOverrides: Map<String, String>,
        torModeEnabled: Boolean,
        torDataDirectory: String?,
        torExecutablePath: String?,
        multiHopEntry: MultiHopEndpoint?
    ): String {
        require(port in 1..65535) { "Invalid AWG port: $port" }
        require(targetIp.isNotBlank()) { "AWG endpoint is empty" }
        require(IPV4_LITERAL.matches(targetIp)) { "AWG endpoint must be an IPv4 address" }
        val torDataDir = torDataDirectory?.trim()?.takeIf(String::isNotEmpty)
        val torExecutable = torExecutablePath?.trim()?.takeIf(String::isNotEmpty)
        require(!torModeEnabled || torDataDir != null) { "Tor data directory is required" }
        require(!torModeEnabled || torExecutable != null) { "Tor executable path is required" }
        require(!torModeEnabled || multiHopEntry == null) { "Tor and Multi Hop cannot be enabled together" }
        multiHopEntry?.let {
            require(it.port in 1..65535) { "Invalid Multi Hop entry port: ${it.port}" }
            require(IPV4_LITERAL.matches(it.targetIp)) { "Multi Hop entry must be an IPv4 address" }
            require(it.publicKey.isNotBlank()) { "Multi Hop entry public key is empty" }
        }

        val localPrefix = ipSubnetCalculator.normalizeIp(localIp)
        // Only the outer entry hop uses AWG obfuscation and the selected AWG port.
        // The inner exit hop is plain WireGuard-over-AWG; a non-obfuscated handshake sent
        // to an AWG-obfuscated port is ignored by the exit server.
        val exitPort = if (multiHopEntry != null) MULTI_HOP_EXIT_WIREGUARD_PORT else port
        val proxyChain = proxyChainConfig?.takeIf(String::isNotBlank)
            ?.let(ProxyLinkParser::parseChain)
            .orEmpty()
            .map { proxy ->
                val server = proxy.outbound["server"] as? JsonPrimitive
                val override = server?.content?.let(proxyServerOverrides::get)
                if (override == null) proxy else proxy.copy(
                    outbound = JsonObject(
                        (proxy.outbound - "domain_resolver") + ("server" to JsonPrimitive(override))
                    )
                )
            }
        val exactDomains = SplitTunnelingDomainRule.exactDomains(selectedDomains)
        val domainSuffixes = SplitTunnelingDomainRule.domainSuffixes(selectedDomains)
        val hasDomainRules = exactDomains.isNotEmpty() || domainSuffixes.isNotEmpty()
        val includeUsesAppRouting = isIncludeMode && selectedApps.isNotEmpty()
        val includeUsesDomainRouting = isIncludeMode && !includeUsesAppRouting && hasDomainRules
        val tunnelOutbound = if (torModeEnabled) "tor" else "proton-awg"
        val routeAddresses = when {
            includeUsesAppRouting && allowLan -> (LanExclusionUtils.REFINED_ALLOWED_IPS + "2000::/3").sorted()
            includeUsesAppRouting -> listOf("0.0.0.0/0")
            isIncludeMode && selectedIps.isNotEmpty() && !includeUsesDomainRouting -> selectedIps.sorted()
            allowLan -> (LanExclusionUtils.REFINED_ALLOWED_IPS + "2000::/3").sorted()
            else -> listOf("0.0.0.0/0")
        }
        val routeExcludes = when {
            isIncludeMode -> emptyList()
            else -> selectedIps.sorted()
        }

        fun strings(values: Collection<String>) = JsonArray(values.map(::JsonPrimitive))
        fun awgValue(value: String): JsonPrimitive? = value.takeIf(String::isNotBlank)?.let(::JsonPrimitive)

        val tun = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive("tun"),
            "tag" to JsonPrimitive("proton-tun"),
            "address" to strings(listOf("172.19.0.1/30")),
            "mtu" to JsonPrimitive(1400),
            "auto_route" to JsonPrimitive(true),
            "strict_route" to JsonPrimitive(true),
            "stack" to JsonPrimitive("system"),
            "route_address" to strings(routeAddresses)
        ).apply {
            if (routeExcludes.isNotEmpty()) put("route_exclude_address", strings(routeExcludes))
        }

        val awg = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive("awg"),
            "tag" to JsonPrimitive("proton-awg"),
            "useIntegratedTun" to JsonPrimitive(false),
            "address" to strings(listOf(localPrefix)),
            "private_key" to JsonPrimitive(privateKey),
            "mtu" to JsonPrimitive(1408),
            "peers" to JsonArray(listOf(JsonObject(mapOf(
                "address" to JsonPrimitive(targetIp),
                "port" to JsonPrimitive(exitPort),
                "public_key" to JsonPrimitive(serverPublicKey),
                "allowed_ips" to strings(listOf("0.0.0.0/0")),
                "persistent_keepalive_interval" to JsonPrimitive(25)
            ))))
        ).apply {
            if (multiHopEntry != null) {
                put("detour", JsonPrimitive("proton-awg-entry"))
            } else if (proxyChain.isNotEmpty()) {
                put("detour", JsonPrimitive(proxyChain.first().outbound.getValue("tag").let { (it as JsonPrimitive).content }))
            } else {
                put("jc", JsonPrimitive(obfuscationParams.jc))
                put("jmin", JsonPrimitive(obfuscationParams.jmin))
                put("jmax", JsonPrimitive(obfuscationParams.jmax))
                put("s1", JsonPrimitive(obfuscationParams.s1))
                put("s2", JsonPrimitive(obfuscationParams.s2))
                put("s3", JsonPrimitive(obfuscationParams.s3))
                put("s4", JsonPrimitive(obfuscationParams.s4))
                awgValue(obfuscationParams.h1)?.let { put("h1", it) }
                awgValue(obfuscationParams.h2)?.let { put("h2", it) }
                awgValue(obfuscationParams.h3)?.let { put("h3", it) }
                awgValue(obfuscationParams.h4)?.let { put("h4", it) }
                awgValue(obfuscationParams.i1)?.let { put("i1", it) }
                awgValue(obfuscationParams.i2)?.let { put("i2", it) }
                awgValue(obfuscationParams.i3)?.let { put("i3", it) }
                awgValue(obfuscationParams.i4)?.let { put("i4", it) }
                awgValue(obfuscationParams.i5)?.let { put("i5", it) }
                awgValue(obfuscationParams.headerProtectionKey)?.let { put("header_protection_key", it) }
                awgValue(obfuscationParams.contentPaddingAddition)?.let { put("content_padding_addition", it) }
                awgValue(obfuscationParams.rekeyAfterTime)?.let { put("rekey_after_time", it) }
                awgValue(obfuscationParams.rekeyTimeout)?.let { put("rekey_timeout", it) }
                awgValue(obfuscationParams.rejectAfterTime)?.let { put("reject_after_time", it) }
                awgValue(obfuscationParams.keepaliveTimeout)?.let { put("keepalive_timeout", it) }
                awgValue(obfuscationParams.maxHandshakeAttempts)?.let { put("max_handshake_attempts", it) }
            }
        }

        val peers = JsonArray(listOf(JsonObject(mapOf(
            "address" to JsonPrimitive(targetIp),
            "port" to JsonPrimitive(exitPort),
            "public_key" to JsonPrimitive(serverPublicKey),
            "allowed_ips" to strings(listOf("0.0.0.0/0")),
            "persistent_keepalive_interval" to awgValue(obfuscationParams.persistentKeepalive.takeIf { it.isNotBlank() } ?: "25")!!
        ))))
        awg["peers"] = peers

        val entryAwg = multiHopEntry?.let { entry ->
            linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
                "type" to JsonPrimitive("awg"),
                "tag" to JsonPrimitive("proton-awg-entry"),
                "useIntegratedTun" to JsonPrimitive(false),
                "address" to strings(listOf(localPrefix)),
                "private_key" to JsonPrimitive(privateKey),
                "mtu" to JsonPrimitive(1408),
                "peers" to JsonArray(listOf(JsonObject(mapOf(
                    "address" to JsonPrimitive(entry.targetIp),
                    "port" to JsonPrimitive(entry.port),
                    "public_key" to JsonPrimitive(entry.publicKey),
                    "allowed_ips" to strings(listOf("0.0.0.0/0")),
                    "persistent_keepalive_interval" to awgValue(obfuscationParams.persistentKeepalive.takeIf { it.isNotBlank() } ?: "25")!!
                ))))
            ).apply {
                if (proxyChain.isNotEmpty()) {
                    put("detour", JsonPrimitive(proxyChain.first().outbound.getValue("tag").let { (it as JsonPrimitive).content }))
                } else {
                    put("jc", JsonPrimitive(obfuscationParams.jc))
                    put("jmin", JsonPrimitive(obfuscationParams.jmin))
                    put("jmax", JsonPrimitive(obfuscationParams.jmax))
                    put("s1", JsonPrimitive(obfuscationParams.s1))
                    put("s2", JsonPrimitive(obfuscationParams.s2))
                    put("s3", JsonPrimitive(obfuscationParams.s3))
                    put("s4", JsonPrimitive(obfuscationParams.s4))
                    awgValue(obfuscationParams.h1)?.let { put("h1", it) }
                    awgValue(obfuscationParams.h2)?.let { put("h2", it) }
                    awgValue(obfuscationParams.h3)?.let { put("h3", it) }
                    awgValue(obfuscationParams.h4)?.let { put("h4", it) }
                    awgValue(obfuscationParams.i1)?.let { put("i1", it) }
                    awgValue(obfuscationParams.i2)?.let { put("i2", it) }
                    awgValue(obfuscationParams.i3)?.let { put("i3", it) }
                    awgValue(obfuscationParams.i4)?.let { put("i4", it) }
                    awgValue(obfuscationParams.i5)?.let { put("i5", it) }
                }
            }
        }

        val domainRuleOutbound = if (isIncludeMode) tunnelOutbound else "direct"
        val routeRules = buildList {
            add(JsonObject(mapOf(
                "ip_version" to JsonPrimitive(6),
                "action" to JsonPrimitive("reject")
            )))
            add(JsonObject(mapOf("action" to JsonPrimitive("sniff"))))
            add(JsonObject(mapOf(
                "protocol" to strings(listOf("dns")),
                "action" to JsonPrimitive("hijack-dns")
            )))
            if (torModeEnabled) {
                // Tor's DNSPort maps .onion names into this virtual range. Sending those
                // addresses back to the same Tor instance preserves its internal hostname map
                // and avoids asking public DNS or relying on sing-box FakeIP domain recovery.
                add(JsonObject(mapOf(
                    "ip_cidr" to strings(listOf(TOR_VIRTUAL_ADDR_RANGE)),
                    "network" to strings(listOf("tcp")),
                    "action" to JsonPrimitive("route"),
                    "outbound" to JsonPrimitive("tor")
                )))
            }
            if (exactDomains.isNotEmpty()) {
                add(JsonObject(buildMap {
                    put("domain", strings(exactDomains))
                    if (torModeEnabled && isIncludeMode) put("network", strings(listOf("tcp")))
                    put("action", JsonPrimitive("route"))
                    put("outbound", JsonPrimitive(domainRuleOutbound))
                }))
            }
            if (domainSuffixes.isNotEmpty()) {
                add(JsonObject(buildMap {
                    put("domain_suffix", strings(domainSuffixes))
                    if (torModeEnabled && isIncludeMode) put("network", strings(listOf("tcp")))
                    put("action", JsonPrimitive("route"))
                    put("outbound", JsonPrimitive(domainRuleOutbound))
                }))
            }
            if (includeUsesDomainRouting && selectedIps.isNotEmpty()) {
                add(JsonObject(buildMap {
                    put("ip_cidr", strings(selectedIps.sorted()))
                    if (torModeEnabled) put("network", strings(listOf("tcp")))
                    put("action", JsonPrimitive("route"))
                    put("outbound", JsonPrimitive(tunnelOutbound))
                }))
            }
            if (torModeEnabled) {
                add(JsonObject(mapOf(
                    "network" to strings(listOf("udp")),
                    "action" to JsonPrimitive("reject")
                )))
            }
        }
        val outbounds = buildList {
            addAll(proxyChain.map(ProxyLinkParser.ParsedProxy::outbound))
            if (torModeEnabled) {
                add(JsonObject(mapOf(
                    "type" to JsonPrimitive("tor"),
                    "tag" to JsonPrimitive("tor"),
                    "data_directory" to JsonPrimitive(requireNotNull(torDataDir)),
                    "executable_path" to JsonPrimitive(requireNotNull(torExecutable)),
                    "detour" to JsonPrimitive("proton-awg"),
                    "torrc" to JsonObject(mapOf(
                        "ClientOnly" to JsonPrimitive("1"),
                        "SafeLogging" to JsonPrimitive("1"),
                        "DNSPort" to JsonPrimitive("127.0.0.1:$TOR_DNS_PORT"),
                        "AutomapHostsOnResolve" to JsonPrimitive("1"),
                        "VirtualAddrNetworkIPv4" to JsonPrimitive(TOR_VIRTUAL_ADDR_RANGE)
                    ))
                )))
            }
            if (hasDomainRules) {
                add(JsonObject(mapOf(
                    "type" to JsonPrimitive("direct"),
                    "tag" to JsonPrimitive("direct")
                )))
            }
        }

        val selectedDnsServer = if (torModeEnabled && isPrivateIpv4(dnsServer)) TOR_FALLBACK_DNS else dnsServer
        val config = JsonObject(mapOf(
            "log" to JsonObject(mapOf(
                "level" to JsonPrimitive(if (netShieldRuleSets.isEmpty()) "info" else "debug"),
                "timestamp" to JsonPrimitive(true)
            )),
            "dns" to JsonObject(buildMap {
                put("servers", JsonArray(buildList {
                    if (proxyChain.isNotEmpty()) add(JsonObject(mapOf(
                        "type" to JsonPrimitive("udp"),
                        "tag" to JsonPrimitive("bootstrap-dns"),
                        "server" to JsonPrimitive("1.1.1.1"),
                        "server_port" to JsonPrimitive(53)
                    )))
                    if (torModeEnabled) add(JsonObject(mapOf(
                        "type" to JsonPrimitive("udp"),
                        "tag" to JsonPrimitive("tor-dns"),
                        "server" to JsonPrimitive("127.0.0.1"),
                        "server_port" to JsonPrimitive(TOR_DNS_PORT)
                    )))
                    add(JsonObject(mapOf(
                        "type" to JsonPrimitive(if (torModeEnabled) "tcp" else "udp"),
                        "tag" to JsonPrimitive("proton-dns"),
                        "server" to JsonPrimitive(selectedDnsServer),
                        "server_port" to JsonPrimitive(53),
                        "detour" to JsonPrimitive(tunnelOutbound)
                    )))
                }))
                // The bootstrap resolver is only for proxy-host resolution. Without an explicit
                // final server sing-box selects the first entry, which made Cloudflare handle all
                // DNS traffic whenever a proxy chain was enabled.
                put("final", JsonPrimitive("proton-dns"))
                val dnsRules = buildList {
                    if (torModeEnabled) {
                        add(JsonObject(mapOf(
                            "domain_suffix" to strings(listOf("onion")),
                            "action" to JsonPrimitive("route"),
                            "server" to JsonPrimitive("tor-dns")
                        )))
                    }
                    addAll(netShieldRuleSets.map { ruleSet ->
                        JsonObject(mapOf(
                            "rule_set" to strings(listOf(ruleSet.tag)),
                            "action" to JsonPrimitive("reject")
                        ))
                    })
                }
                if (dnsRules.isNotEmpty()) put("rules", JsonArray(dnsRules))
                put("strategy", JsonPrimitive("ipv4_only"))
            }),
            "inbounds" to JsonArray(listOf(JsonObject(tun))),
            "endpoints" to JsonArray(buildList {
                entryAwg?.let { add(JsonObject(it)) }
                add(JsonObject(awg))
            }),
            "outbounds" to JsonArray(outbounds),
            "route" to JsonObject(buildMap {
                put("auto_detect_interface", JsonPrimitive(true))
                put("rules", JsonArray(routeRules))
                if (netShieldRuleSets.isNotEmpty()) {
                    put("rule_set", JsonArray(netShieldRuleSets.map { ruleSet ->
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("local"),
                            "tag" to JsonPrimitive(ruleSet.tag),
                            "format" to JsonPrimitive("source"),
                            "path" to JsonPrimitive(ruleSet.path)
                        ))
                    }))
                }
                put("final", JsonPrimitive(if (includeUsesDomainRouting) "direct" else tunnelOutbound))
            })
        ))
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun isPrivateIpv4(address: String): Boolean {
        val parts = address.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            parts[0] == 127
    }
}
