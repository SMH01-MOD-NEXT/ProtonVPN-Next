/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import kotlin.concurrent.thread

class HandshakeTimestampSourceTest {

    @Test
    fun timestampsStrictlyIncreasePerServerEvenWithAFrozenClock() {
        val source = HandshakeTimestampSource { NOW }
        var previous = source.next("server")
        repeat(100) {
            val next = source.next("server")
            assertTrue(compareUnsigned(next, previous) > 0)
            previous = next
        }
    }

    @Test
    fun serversAreIndependent() {
        val source = HandshakeTimestampSource { NOW }
        source.next("a")
        source.next("a")
        assertArrayEquals(WireGuardCrypto.tai64n(NOW), source.next("b"))
    }

    @Test
    fun realTimeIsUsedOnceItCatchesUp() {
        var now = NOW
        val source = HandshakeTimestampSource { now }
        assertArrayEquals(WireGuardCrypto.tai64n(NOW), source.next("a"))
        now += 5_000_000_000L
        assertArrayEquals(WireGuardCrypto.tai64n(now), source.next("a"))
    }

    @Test
    fun clockGoingBackwardsKeepsTheOrder() {
        var now = NOW
        val source = HandshakeTimestampSource { now }
        val first = source.next("a")
        now -= 60_000_000_000L
        assertTrue(compareUnsigned(source.next("a"), first) > 0)
    }

    @Test
    fun concurrentCallersNeverShareATimestamp() {
        val source = HandshakeTimestampSource { NOW }
        val results = Collections.synchronizedList(ArrayList<String>())
        val workers = List(8) { thread { repeat(100) { results += source.next("a").toHex() } } }
        workers.forEach { it.join() }
        assertEquals(800, results.toSet().size)
    }

    private companion object {
        const val NOW = 1_700_000_000_000_000_000L
    }
}
