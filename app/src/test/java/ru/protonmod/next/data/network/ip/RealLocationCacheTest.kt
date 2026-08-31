package ru.protonmod.next.data.network.ip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLocationCacheTest {

    @Test
    fun `an address and its country survive a trip through storage`() {
        val snapshot = RealLocationCache.sanitise("149.102.244.111", "pl")

        assertEquals("149.102.244.111", snapshot?.ip)
        assertEquals("PL", snapshot?.countryCode)
    }

    @Test
    fun `the mapped form a mirror returns is kept as a plain address`() {
        assertEquals("203.0.113.7", RealLocationCache.sanitise("::ffff:203.0.113.7", "")?.ip)
    }

    @Test
    fun `a real IPv6 address is kept as it is`() {
        assertEquals("2001:db8::1", RealLocationCache.sanitise("2001:db8::1", "PL")?.ip)
    }

    @Test
    fun `the word shown when a lookup fails is never remembered`() {
        for (raw in listOf("Unknown", "Неизвестно", "unbekannt", "", "   ", null)) {
            assertNull("'$raw' must not be remembered", RealLocationCache.sanitise(raw, "PL"))
        }
    }

    @Test
    fun `a word spelled in hex digits is not an address`() {
        assertNull(RealLocationCache.sanitise("abc", "PL"))
    }

    @Test
    fun `an address with no country is still worth remembering`() {
        val snapshot = RealLocationCache.sanitise("149.102.244.111", null)

        assertEquals("149.102.244.111", snapshot?.ip)
        assertNull(snapshot?.countryCode)
    }

    @Test
    fun `a country code that names nowhere is dropped`() {
        for (code in listOf("XX", "T1", "x", "PLL", "12", "  ")) {
            val snapshot = RealLocationCache.sanitise("203.0.113.7", code)

            assertEquals("'$code' must not cost us the address", "203.0.113.7", snapshot?.ip)
            assertNull("'$code' names nowhere", snapshot?.countryCode)
        }
    }

    @Test
    fun `the address is not asked for again while a tunnel is up`() {
        assertFalse(RealLocationCache.shouldRefresh(tunnelActive = true, hasCachedAnswer = true))
    }

    @Test
    fun `a tunnel with nothing remembered is still asked so the map has a home`() {
        assertTrue(RealLocationCache.shouldRefresh(tunnelActive = true, hasCachedAnswer = false))
    }

    @Test
    fun `with no tunnel up the address is always refreshed`() {
        assertTrue(RealLocationCache.shouldRefresh(tunnelActive = false, hasCachedAnswer = true))
        assertTrue(RealLocationCache.shouldRefresh(tunnelActive = false, hasCachedAnswer = false))
    }
}
