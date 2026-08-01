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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwgBoxConfigGeneratorTest {
    private val generator = AwgBoxConfigGeneratorImpl(object : IpSubnetCalculator {
        override fun isValidIpOrCidr(input: String) = true
        override fun normalizeIp(ip: String) = if ('/' in ip) ip else "$ip/32"
        override fun complementOfExcluded(excludedCidrs: Collection<String>) = listOf("0.0.0.0/0")
    })

    @Test
    fun `builds awg2 endpoint and sing-box tun topology`() {
        val config = generator.buildConfig(
            serverPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            privateKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.10",
            selectedApps = setOf("org.example.exclude"),
            selectedIps = setOf("203.0.113.0/24"),
            port = 443,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 4,
                jmin = 40,
                jmax = 70,
                s1 = 20,
                s2 = 30,
                s3 = 40,
                s4 = 50,
                h1 = "1-3",
                h2 = "4",
                h3 = "5",
                h4 = "6",
                i1 = "<b 0x01>",
                i2 = "<r 8>",
                i3 = "",
                i4 = "",
                i5 = ""
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val tun = root.getValue("inbounds").jsonArray.single().jsonObject
        val awg = root.getValue("endpoints").jsonArray.single().jsonObject
        val peer = awg.getValue("peers").jsonArray.single().jsonObject

        assertEquals("tun", tun.getValue("type").jsonPrimitive.content)
        assertEquals("system", tun.getValue("stack").jsonPrimitive.content)
        assertTrue(tun.getValue("strict_route").jsonPrimitive.content.toBoolean())
        assertEquals("awg", awg.getValue("type").jsonPrimitive.content)
        assertEquals("10.2.0.2/32", awg.getValue("address").jsonArray.single().jsonPrimitive.content)
        assertEquals("4", awg.getValue("jc").jsonPrimitive.content)
        assertEquals("50", awg.getValue("s4").jsonPrimitive.content)
        assertEquals("<r 8>", awg.getValue("i2").jsonPrimitive.content)
        assertEquals("192.0.2.10", peer.getValue("address").jsonPrimitive.content)
        assertEquals("443", peer.getValue("port").jsonPrimitive.content)
        val route = root.getValue("route").jsonObject
        val ipv6Rule = route.getValue("rules").jsonArray.first().jsonObject
        assertEquals("6", ipv6Rule.getValue("ip_version").jsonPrimitive.content)
        assertEquals("reject", ipv6Rule.getValue("action").jsonPrimitive.content)
        assertEquals("proton-awg", route.getValue("final").jsonPrimitive.content)
        assertFalse(root.getValue("dns").jsonObject.getValue("servers").jsonArray
            .map { it.jsonObject }
            .any { it.getValue("tag").jsonPrimitive.content == "tor-dns" })
    }

    @Test
    fun `include app mode tunnels all app traffic despite IP and domain selections`() {
        val privateKey = "PRIVATE_KEY_TEST_VALUE"
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY",
            privateKey = privateKey,
            localIp = "10.2.0.2/32",
            dnsServer = "10.2.0.1",
            targetIp = "198.51.100.1",
            isIncludeMode = true,
            selectedApps = setOf("org.example.only"),
            selectedIps = setOf("8.8.8.8/32"),
            selectedDomains = setOf("*.example.com"),
            port = 51820,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val tun = root.getValue("inbounds").jsonArray.single().jsonObject
        val route = root.getValue("route").jsonObject
        val routes = tun.getValue("route_address").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(listOf("0.0.0.0/0"), routes)
        assertTrue("include_package" in tun)
        assertFalse("exclude_package" in tun)
        assertEquals("proton-awg", route.getValue("final").jsonPrimitive.content)
        assertEquals(1, Regex(Regex.escape(privateKey)).findAll(config).count())
    }

    @Test
    fun `proxy chain detours AWG and omits AWG obfuscation fields`() {
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY",
            privateKey = "PRIVATE_KEY",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "198.51.100.1",
            port = 51820,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 10, jmin = 20, jmax = 30, s1 = 40, s2 = 50,
                h1 = "1", h2 = "2", h3 = "3", h4 = "4", i1 = "junk"
            ),
            proxyChainConfig = "vless://123e4567-e89b-12d3-a456-426614174000@proxy.example:443?encryption=none",
            proxyServerOverrides = mapOf("proxy.example" to "192.0.2.10")
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val awg = root.getValue("endpoints").jsonArray.single().jsonObject
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        val proxy = outbounds.first { it.getValue("tag").jsonPrimitive.content == "proxy-1" }
        val dnsServers = root.getValue("dns").jsonObject.getValue("servers").jsonArray.map { it.jsonObject }
        val dns = root.getValue("dns").jsonObject
        val bootstrapDns = dnsServers.first { it.getValue("tag").jsonPrimitive.content == "bootstrap-dns" }
        val selectedDns = dnsServers.first { it.getValue("tag").jsonPrimitive.content == "proton-dns" }

        assertEquals("proxy-1", awg.getValue("detour").jsonPrimitive.content)
        assertFalse("jc" in awg)
        assertFalse("i1" in awg)
        assertEquals(1, outbounds.size)
        assertEquals("vless", proxy.getValue("type").jsonPrimitive.content)
        assertEquals("192.0.2.10", proxy.getValue("server").jsonPrimitive.content)
        assertFalse("domain_resolver" in proxy)
        assertEquals("xudp", proxy.getValue("packet_encoding").jsonPrimitive.content)
        assertEquals("1.1.1.1", bootstrapDns.getValue("server").jsonPrimitive.content)
        assertFalse("detour" in bootstrapDns)
        assertEquals("10.2.0.1", selectedDns.getValue("server").jsonPrimitive.content)
        assertEquals("proton-dns", dns.getValue("final").jsonPrimitive.content)
        assertEquals(2, dnsServers.size)
    }

    @Test
    fun `uses selected resolver as final DNS server`() {
        listOf("8.8.8.8", "94.140.14.14", "10.2.0.1").forEach { resolver ->
            val config = generator.buildConfig(
                serverPublicKey = "PUBLIC_KEY",
                privateKey = "PRIVATE_KEY",
                localIp = "10.2.0.2",
                dnsServer = resolver,
                targetIp = "198.51.100.1",
                port = 51820,
                obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                    jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                    h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
                )
            )

            val dns = Json.parseToJsonElement(config).jsonObject.getValue("dns").jsonObject
            val selectedDns = dns.getValue("servers").jsonArray
                .map { it.jsonObject }
                .first { it.getValue("tag").jsonPrimitive.content == "proton-dns" }

            assertEquals(resolver, selectedDns.getValue("server").jsonPrimitive.content)
            assertEquals("proton-dns", dns.getValue("final").jsonPrimitive.content)
        }
    }

    @Test
    fun `exclude mode routes wildcard domains outside VPN`() {
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY",
            privateKey = "PRIVATE_KEY",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "198.51.100.1",
            selectedDomains = setOf("*.ru", "*.рф", "example.com"),
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val route = root.getValue("route").jsonObject
        val rules = route.getValue("rules").jsonArray.map { it.jsonObject }
        val exactRule = rules.first { "domain" in it }
        val suffixRule = rules.first { "domain_suffix" in it }
        val directOutbound = root.getValue("outbounds").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("tag").jsonPrimitive.content == "direct" }

        assertEquals(listOf("example.com"), exactRule.getValue("domain").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("ru", "xn--p1ai"),
            suffixRule.getValue("domain_suffix").jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("direct", exactRule.getValue("outbound").jsonPrimitive.content)
        assertEquals("direct", suffixRule.getValue("outbound").jsonPrimitive.content)
        assertEquals("direct", directOutbound.getValue("type").jsonPrimitive.content)
        assertEquals("proton-awg", route.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun `include mode captures wildcard domains and bypasses unmatched traffic`() {
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY",
            privateKey = "PRIVATE_KEY",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "198.51.100.1",
            isIncludeMode = true,
            selectedIps = setOf("8.8.8.8/32"),
            selectedDomains = setOf("*.ru"),
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val tun = root.getValue("inbounds").jsonArray.single().jsonObject
        val route = root.getValue("route").jsonObject
        val rules = route.getValue("rules").jsonArray.map { it.jsonObject }
        val suffixRule = rules.first { "domain_suffix" in it }
        val ipRule = rules.first { "ip_cidr" in it }

        assertEquals(listOf("0.0.0.0/0"), tun.getValue("route_address").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("proton-awg", suffixRule.getValue("outbound").jsonPrimitive.content)
        assertEquals("proton-awg", ipRule.getValue("outbound").jsonPrimitive.content)
        assertEquals("direct", route.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun `tor mode routes through Tor while global exclusions stay direct`() {
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY", privateKey = "PRIVATE_KEY",
            localIp = "10.2.0.2", dnsServer = "10.2.0.1", targetIp = "198.51.100.1",
            selectedDomains = setOf("*.ru"), torModeEnabled = true,
            torDataDirectory = "/data/user/0/app/no_backup/tor",
            torExecutablePath = "/data/app/lib/arm64/libtor.so",
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val route = root.getValue("route").jsonObject
        val rules = route.getValue("rules").jsonArray.map { it.jsonObject }
        val tor = root.getValue("outbounds").jsonArray.map { it.jsonObject }
            .single { it.getValue("tag").jsonPrimitive.content == "tor" }
        val dnsConfig = root.getValue("dns").jsonObject
        val dnsServers = dnsConfig.getValue("servers").jsonArray.map { it.jsonObject }
        val dns = dnsServers.single { it.getValue("tag").jsonPrimitive.content == "proton-dns" }
        val torDns = dnsServers.single { it.getValue("tag").jsonPrimitive.content == "tor-dns" }
        val onionDnsRule = dnsConfig.getValue("rules").jsonArray.map { it.jsonObject }
            .single { "domain_suffix" in it }
        val torVirtualAddressRule = rules.single {
            it["ip_cidr"]?.jsonArray?.any { cidr -> cidr.jsonPrimitive.content == "198.18.0.0/15" } == true
        }
        val torrc = tor.getValue("torrc").jsonObject
        assertEquals("tor", route.getValue("final").jsonPrimitive.content)
        assertEquals("proton-awg", tor.getValue("detour").jsonPrimitive.content)
        assertEquals("/data/app/lib/arm64/libtor.so", tor.getValue("executable_path").jsonPrimitive.content)
        val excludedDomainRule = rules.single {
            it["domain_suffix"]?.jsonArray?.any { suffix -> suffix.jsonPrimitive.content == "ru" } == true
        }
        assertEquals("direct", excludedDomainRule.getValue("outbound").jsonPrimitive.content)
        assertTrue(rules.any { it["action"]?.jsonPrimitive?.content == "reject" && "network" in it })
        assertEquals("tcp", dns.getValue("type").jsonPrimitive.content)
        assertEquals("1.1.1.1", dns.getValue("server").jsonPrimitive.content)
        assertEquals("tor", dns.getValue("detour").jsonPrimitive.content)
        assertEquals("udp", torDns.getValue("type").jsonPrimitive.content)
        assertEquals("127.0.0.1", torDns.getValue("server").jsonPrimitive.content)
        assertEquals("19053", torDns.getValue("server_port").jsonPrimitive.content)
        assertEquals("tor-dns", onionDnsRule.getValue("server").jsonPrimitive.content)
        assertEquals("tor", torVirtualAddressRule.getValue("outbound").jsonPrimitive.content)
        assertEquals("127.0.0.1:19053", torrc.getValue("DNSPort").jsonPrimitive.content)
        assertEquals("1", torrc.getValue("AutomapHostsOnResolve").jsonPrimitive.content)
        assertEquals("198.18.0.0/15", torrc.getValue("VirtualAddrNetworkIPv4").jsonPrimitive.content)
    }

    @Test
    fun `builds awg3 endpoint with mimicry parameters`() {
        val config = generator.buildConfig(
            serverPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            privateKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.10",
            port = 443,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 4, jmin = 40, jmax = 70, s1 = 12, s2 = 12, s3 = 12, s4 = 12,
                h1 = "1", h2 = "2", h3 = "3", h4 = "4", i1 = "mimic",
                headerProtectionKey = "dGVzdF9rZXlfMzJfYnl0ZXNfbG9uZ19mb3JfYXdnMw==",
                contentPaddingAddition = "0-100",
                rekeyAfterTime = "120-180",
                persistentKeepalive = "20-30"
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val awg = root.getValue("endpoints").jsonArray.single().jsonObject
        val peer = awg.getValue("peers").jsonArray.single().jsonObject

        assertEquals("dGVzdF9rZXlfMzJfYnl0ZXNfbG9uZ19mb3JfYXdnMw==", awg.getValue("header_protection_key").jsonPrimitive.content)
        assertEquals("0-100", awg.getValue("content_padding_addition").jsonPrimitive.content)
        assertEquals("120-180", awg.getValue("rekey_after_time").jsonPrimitive.content)
        assertEquals("20-30", peer.getValue("persistent_keepalive_interval").jsonPrimitive.content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid endpoint port`() {
        generator.buildConfig(
            serverPublicKey = "public",
            privateKey = "private",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.1",
            port = 0,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )
    }
}
