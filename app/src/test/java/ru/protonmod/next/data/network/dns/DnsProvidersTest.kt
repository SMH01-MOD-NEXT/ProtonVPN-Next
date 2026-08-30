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

package ru.protonmod.next.data.network.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is a security boundary, not a config list: if a Russian resolver
 * ever reaches it, every other defence in the DNS stack is bypassed by design.
 * These tests pin that boundary.
 */
class DnsProvidersTest {

    @Test
    fun `rejects Yandex resolvers`() {
        assertTrue(DnsProviders.isDenied("77.88.8.8"))
        assertTrue(DnsProviders.isDenied("77.88.8.1"))
        assertTrue(DnsProviders.isDenied("77.88.8.88"))
    }

    @Test
    fun `rejects Yandex addresses not enumerated individually`() {
        // Covered by the /24 prefix rule rather than the literal set.
        assertTrue(DnsProviders.isDenied("77.88.8.222"))
        assertTrue(DnsProviders.isDenied("2a02:6b8::feed:0ff"))
    }

    @Test
    fun `rejects Russian ISP and filtering resolvers`() {
        assertTrue("SkyDNS", DnsProviders.isDenied("193.58.251.251"))
        assertTrue("Comss.one", DnsProviders.isDenied("83.220.169.155"))
        assertTrue("Rostelecom", DnsProviders.isDenied("213.158.0.6"))
        assertTrue("MTS", DnsProviders.isDenied("212.188.4.10"))
        assertTrue("Beeline", DnsProviders.isDenied("217.118.66.243"))
        assertTrue("MegaFon", DnsProviders.isDenied("83.149.32.15"))
    }

    @Test
    fun `normalises user input before deciding`() {
        // What a user actually pastes: stray spaces, brackets, mixed case.
        assertTrue(DnsProviders.isDenied("  77.88.8.8  "))
        assertTrue(DnsProviders.isDenied("[2A02:6B8::FEED:0FF]"))
    }

    @Test
    fun `allows an empty value so clearing custom DNS still works`() {
        assertFalse(DnsProviders.isDenied(""))
        assertFalse(DnsProviders.isDenied("   "))
    }

    @Test
    fun `allows trusted resolvers`() {
        assertFalse(DnsProviders.isDenied("1.1.1.1"))
        assertFalse(DnsProviders.isDenied("8.8.8.8"))
        assertFalse(DnsProviders.isDenied("9.9.9.9"))
    }

    @Test
    fun `no bundled provider address is on the denylist`() {
        // Guards against a future edit adding an address that the denylist,
        // or one of its prefixes, silently blocks at runtime.
        for (provider in DnsProviders.ALL) {
            for (address in provider.addresses) {
                assertFalse(
                    "${provider.displayName} address $address is denied",
                    DnsProviders.isDenied(address)
                )
            }
        }
    }

    @Test
    fun `no provider operates under Russian jurisdiction`() {
        for (provider in DnsProviders.ALL) {
            assertFalse(
                "${provider.displayName} is RU-jurisdiction",
                provider.jurisdiction.equals("RU", ignoreCase = true)
            )
        }
    }

    @Test
    fun `Cloudflare and Google lead the default order`() {
        // The user requires both kept working; ordering decides who wins the race.
        assertEquals(DnsProviders.CLOUDFLARE, DnsProviders.DEFAULT_ORDER[0])
        assertEquals(DnsProviders.GOOGLE, DnsProviders.DEFAULT_ORDER[1])
    }

    @Test
    fun `default order covers every provider exactly once`() {
        assertEquals(DnsProviders.ALL.size, DnsProviders.DEFAULT_ORDER.size)
        assertEquals(DnsProviders.DEFAULT_ORDER.toSet().size, DnsProviders.DEFAULT_ORDER.size)
    }

    @Test
    fun `byId resolves known providers and rejects unknown ones`() {
        assertNotNull(DnsProviders.byId(DnsProviders.CLOUDFLARE))
        assertNotNull(DnsProviders.byId(DnsProviders.QUAD9))
        assertNull(DnsProviders.byId("yandex"))
        assertNull(DnsProviders.byId(""))
    }

    @Test
    fun `every provider can be reached without a name lookup`() {
        // The whole design depends on never needing the system resolver to
        // find a resolver, so each entry must carry literal addresses.
        for (provider in DnsProviders.ALL) {
            assertTrue(
                "${provider.displayName} has no bootstrap addresses",
                provider.addresses.isNotEmpty()
            )
            assertTrue(
                "${provider.displayName} has no DoT hostname",
                provider.dotHostname.isNotBlank()
            )
        }
    }

    @Test
    fun `IP-literal DoH endpoints point at an address the provider owns`() {
        // A dohIpUrl whose host is not in `addresses` would fail TLS validation,
        // and would do so only at runtime on a blocked network.
        for (provider in DnsProviders.ALL) {
            val url = provider.dohIpUrl ?: continue
            val host = url.removePrefix("https://").substringBefore('/')
            assertTrue(
                "${provider.displayName} dohIpUrl host $host is not one of its addresses",
                host in provider.addresses
            )
        }
    }

    @Test
    fun `all endpoints are HTTPS`() {
        for (provider in DnsProviders.ALL) {
            assertTrue(provider.dohHostUrl.startsWith("https://"))
            provider.dohIpUrl?.let { assertTrue(it.startsWith("https://")) }
        }
    }
}
