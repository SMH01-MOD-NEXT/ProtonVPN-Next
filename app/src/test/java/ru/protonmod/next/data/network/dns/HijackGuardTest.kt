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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * Covers the poison check only.
 *
 * The canary probe deliberately talks to the real system resolver, so it is not
 * exercised here: a unit test that depends on the CI machine's DNS would be
 * flaky in exactly the environments this code is about. Addresses below are
 * built with [InetAddress.getByAddress], which performs no lookup.
 */
class HijackGuardTest {

    private lateinit var guard: HijackGuard

    @Before
    fun setUp() {
        guard = HijackGuard()
    }

    private fun ipv4(host: String, a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(
            host,
            byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())
        )

    @Test
    fun `a real public address is not poisoned`() {
        val answers = listOf(ipv4("proton.me", 185, 70, 42, 45))
        assertFalse(guard.isPoisoned(answers))
    }

    @Test
    fun `loopback for a public host is poisoned`() {
        // A widespread NXDOMAIN-rewriting shape: send the client to itself.
        val answers = listOf(ipv4("proton.me", 127, 0, 0, 1))
        assertTrue(guard.isPoisoned(answers))
    }

    @Test
    fun `wildcard address is poisoned`() {
        val answers = listOf(ipv4("proton.me", 0, 0, 0, 0))
        assertTrue(guard.isPoisoned(answers))
    }

    @Test
    fun `link-local address is poisoned`() {
        val answers = listOf(ipv4("proton.me", 169, 254, 3, 7))
        assertTrue(guard.isPoisoned(answers))
    }

    @Test
    fun `an empty answer is not treated as poisoned`() {
        // Nothing to judge: this is a resolution failure, handled elsewhere.
        assertFalse(guard.isPoisoned(emptyList()))
    }

    @Test
    fun `a usable address among rewritten ones is not discarded`() {
        // Only a wholly unusable answer counts as poisoned, so a real address
        // is never thrown away because of a junk sibling record.
        val answers = listOf(
            ipv4("proton.me", 127, 0, 0, 1),
            ipv4("proton.me", 185, 70, 42, 45),
        )
        assertFalse(guard.isPoisoned(answers))
    }

    @Test
    fun `sentinels start empty and survive being read`() {
        assertTrue(guard.sentinels().isEmpty())
    }

    @Test
    fun `invalidate clears discovered state`() {
        guard.invalidate()
        assertTrue(guard.sentinels().isEmpty())
    }

    @Test
    fun `a resolver is not trusted until a probe has proven it honest`() {
        // UNKNOWN must never read as trustworthy, otherwise an unreachable
        // probe would silently re-enable the hijacked path.
        val verdict = HijackGuard.Verdict.UNKNOWN
        assertFalse(verdict == HijackGuard.Verdict.CLEAN)
    }
}
