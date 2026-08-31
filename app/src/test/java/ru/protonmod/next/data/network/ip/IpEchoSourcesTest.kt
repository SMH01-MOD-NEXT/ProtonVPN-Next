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

package ru.protonmod.next.data.network.ip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpEchoSourcesTest {

    private fun ids(sources: List<IpEchoSources.Source>) = sources.map { it.id }

    @Test
    fun `every source this project owns is asked before any third party`() {
        for (russian in listOf(true, false)) {
            val ordered = IpEchoSources.ordered(isRussianRegion = russian)
            val lastOwn = ordered.indexOfLast { it.isOwn }
            val firstForeign = ordered.indexOfFirst { !it.isOwn }

            assertTrue("our own deployments must come first", lastOwn < firstForeign)
        }
    }

    @Test
    fun `Cloudflare is asked last of ours inside Russia and first elsewhere`() {
        assertEquals("deno", ids(IpEchoSources.ordered(isRussianRegion = true)).first())
        assertEquals("cloudflare", ids(IpEchoSources.ordered(isRussianRegion = false)).first())

        val inRussia = ids(IpEchoSources.ordered(isRussianRegion = true))
        assertTrue(inRussia.indexOf("deno") < inRussia.indexOf("cloudflare"))
    }

    @Test
    fun `an event bypass is tried ahead of Cloudflare inside Russia`() {
        val ordered = ids(
            IpEchoSources.ordered(
                isRussianRegion = true,
                eventBypassUrl = "https://bypass.invalid/api/"
            )
        )

        assertEquals(listOf("deno", "event", "cloudflare"), ordered.filter { it != "api.myip.com" && it != "freeipapi" })
    }

    @Test
    fun `a cleartext bypass URL is refused rather than used`() {
        // The whole point of the request is "what is my address". Asking that
        // over plain HTTP would broadcast the answer.
        val ordered = ids(
            IpEchoSources.ordered(isRussianRegion = true, eventBypassUrl = "http://bypass.invalid/")
        )

        assertFalse(ordered.contains("event"))
    }

    @Test
    fun `an unusable bypass value is skipped without breaking the chain`() {
        for (raw in listOf("", "   ", "not a url", "ftp://bypass.invalid")) {
            val ordered = ids(IpEchoSources.ordered(isRussianRegion = false, eventBypassUrl = raw))

            assertFalse("'$raw' must not become a source", ordered.contains("event"))
            assertEquals("cloudflare", ordered.first())
        }
    }

    @Test
    fun `no source is ever plain HTTP`() {
        val ordered = IpEchoSources.ordered(
            isRussianRegion = true,
            eventBypassUrl = "https://bypass.invalid"
        )

        for (source in ordered) {
            assertTrue(source.url, source.url.startsWith("https://"))
        }
    }

    @Test
    fun `the mapped form the Deno mirror returned reads as a plain address`() {
        assertEquals("149.102.244.111", IpEchoSources.normaliseAddress("::ffff:149.102.244.111"))
        assertEquals("149.102.244.111", IpEchoSources.normaliseAddress("::FFFF:149.102.244.111"))
        assertEquals("149.102.244.111", IpEchoSources.normaliseAddress("  149.102.244.111  "))
        assertEquals("149.102.244.111", IpEchoSources.normaliseAddress("149.102.244.111:51234"))
        assertEquals("2001:db8::1", IpEchoSources.normaliseAddress("[2001:db8::1]:443"))
    }

    @Test
    fun `a real IPv6 address is left alone`() {
        // Nothing but colons, so it must never be read as a host:port pair.
        assertEquals("2001:db8::1", IpEchoSources.normaliseAddress("2001:db8::1"))
        assertEquals("::1", IpEchoSources.normaliseAddress("::1"))
        assertEquals("", IpEchoSources.normaliseAddress(""))
        assertEquals("", IpEchoSources.normaliseAddress("   "))
    }

    @Test
    fun `normalising an address twice changes nothing`() {
        val raws = listOf("::ffff:1.2.3.4", "[2001:db8::1]:443", "1.2.3.4", "::1", "")

        for (raw in raws) {
            val once = IpEchoSources.normaliseAddress(raw)

            assertEquals(once, IpEchoSources.normaliseAddress(once))
        }
    }

    @Test
    fun `the country probe is one of our own deployments`() {
        val probe = IpEchoSources.countryProbeUrl()

        assertTrue(probe.startsWith(IpEchoSources.CLOUDFLARE_ORIGIN))
        assertTrue(probe.endsWith(IpEchoSources.WHOAMI_PATH))
    }
}
