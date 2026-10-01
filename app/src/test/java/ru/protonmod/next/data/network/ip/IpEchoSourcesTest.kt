/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.ip

import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry
import ru.protonmod.next.data.model.eventbypass.EventBypassCache
import ru.protonmod.next.data.network.ProxyEndpoints

class IpEchoSourcesTest {
    private fun cached(vararg entries: EventBypassEntry) = EventBypassCache.encode(entries.toList())
    private val vercel = EventBypassEntry(id = "vercel", name = "Vercel", url = "https://dynamic.vercel.app/api/", enabled = true)
    @Test fun `catalogue uses shared builtins and never invents Vercel`() {
        val catalogue = IpEchoSources.catalogue()
        assertEquals(ProxyEndpoints.builtIn.map { it.id }, catalogue.filter { it.kind == IpEchoSources.Kind.BUILT_IN }.map { it.id })
        assertTrue(catalogue.none { it.kind == IpEchoSources.Kind.EVENT || it.id == "vercel" })
    }
    @Test fun `Vercel is exactly one named dynamic event with query echo route`() {
        val events = IpEchoSources.catalogue(cached(vercel, vercel)).filter { it.kind == IpEchoSources.Kind.EVENT }
        assertEquals(1, events.size)
        assertEquals("Vercel", events.single().name)
        val url = events.single().urls.first().toHttpUrl()
        assertEquals("/api", url.encodedPath)
        assertEquals("/__proxy/whoami", url.queryParameter("__path"))
    }
    @Test fun `invalid disabled plaintext and corrupt event data cannot crash or create providers`() {
        for (raw in listOf("", "garbage", "[]", cached(vercel.copy(enabled = false)), cached(vercel.copy(url = "https://")),
            cached(vercel.copy(url = "http://insecure.example/api/")), cached(vercel.copy(url = "https://u:p@example.com/")))) {
            assertTrue(IpEchoSources.catalogue(raw).none { it.kind == IpEchoSources.Kind.EVENT })
        }
    }
    @Test fun `new rotating endpoint never inherits old event permission`() {
        val old = IpEchoSources.catalogue(cached(vercel)).first { it.kind == IpEchoSources.Kind.EVENT }
        val changed = IpEchoSources.catalogue(cached(vercel.copy(url = "https://replacement.example/api/")))
        assertTrue(IpEchoSources.selected(false, changed, setOf(old.id)).isEmpty())
        assertTrue(IpEchoSources.selected(false, changed, setOf("event", "vercel")).isEmpty())
    }
    @Test fun `only explicitly selected catalogue providers participate in all probes`() {
        val catalogue = IpEchoSources.catalogue(cached(vercel))
        for (russian in listOf(true, false)) for (service in catalogue) {
            val sources = IpEchoSources.selected(russian, catalogue, setOf(service.id))
            assertTrue(sources.isNotEmpty())
            assertTrue(sources.all { it.id == service.id })
            assertTrue(IpEchoSources.selectedCountryProbes(russian, catalogue, setOf(service.id)).all { it.id == service.id })
        }
        assertTrue(IpEchoSources.selected(false, catalogue, emptySet()).isEmpty())
        assertTrue(IpEchoSources.selectedCountryProbes(true, catalogue, emptySet()).isEmpty())
        assertTrue(IpEchoSources.selected(false, catalogue, IpEchoSources.DEFAULT_ENABLED_IDS).none { it.isOwn })
    }
    @Test fun `legacy selected entry works offline but retired cache is not resurrected`() {
        assertEquals(1, IpEchoSources.catalogue("", vercel.url, vercel.name).count { it.kind == IpEchoSources.Kind.EVENT })
        assertEquals(0, IpEchoSources.catalogue("[]", vercel.url, vercel.name).count { it.kind == IpEchoSources.Kind.EVENT })
    }
    @Test fun `duplicate builtin endpoint in remote config is not shown twice`() {
        val cache = cached(vercel.copy(url = ProxyEndpoints.DENO_URL))
        assertTrue(IpEchoSources.catalogue(cache).none { it.kind == IpEchoSources.Kind.EVENT })
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

}
