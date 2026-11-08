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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProbePacketTest {

    private val tunnel = ipv4Address(10, 2, 0, 2)
    private val resolver = ipv4Address(10, 2, 0, 1)

    private fun u16(data: ByteArray, offset: Int) =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    /** RFC 1071 sum: the words covered by a correct checksum add up to 0xFFFF. */
    private fun onesComplementSum(data: ByteArray, offset: Int, length: Int, initial: Long = 0L): Long {
        var sum = initial
        var index = 0
        while (index < length) {
            val high = data[offset + index].toInt() and 0xFF
            val low = if (index + 1 < length) data[offset + index + 1].toInt() and 0xFF else 0
            sum += ((high shl 8) or low).toLong()
            index += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum
    }

    @Test
    fun queryAsksForTheARecordOfExampleCom() {
        assertEquals(
            "1234" + "0100" + "0001" + "0000" + "0000" + "0000" + "076578616d706c6503636f6d00" + "0001" + "0001",
            DnsProbePacket.buildQuery(0x1234).toHex(),
        )
    }

    @Test
    fun ipv4PacketHasValidHeadersAndChecksums() {
        for (queryId in listOf(0, 0xBEEF, 0xFFFF)) {
            val packet = DnsProbePacket.buildIpv4Query(tunnel, resolver, 40_000, queryId)
            val query = DnsProbePacket.buildQuery(queryId)
            val udpLength = 8 + query.size
            assertEquals(20 + udpLength, packet.size)
            assertEquals(0x45, packet[0].toInt() and 0xFF)
            assertEquals(packet.size, u16(packet, 2))
            assertEquals(queryId, u16(packet, 4))
            assertEquals(0x4000, u16(packet, 6))
            assertEquals(64, packet[8].toInt() and 0xFF)
            assertEquals(17, packet[9].toInt() and 0xFF)
            assertArrayEquals(tunnel.address, packet.copyOfRange(12, 16))
            assertArrayEquals(resolver.address, packet.copyOfRange(16, 20))
            assertEquals(0xFFFFL, onesComplementSum(packet, 0, 20))
            assertEquals(40_000, u16(packet, 20))
            assertEquals(DnsProbePacket.DNS_PORT, u16(packet, 22))
            assertEquals(udpLength, u16(packet, 24))
            // Zero would mean "no checksum"; a computed zero is sent as 0xFFFF.
            assertTrue(u16(packet, 26) != 0)
            val pseudoHeader = onesComplementSum(packet, 12, 8) + 17 + udpLength
            assertEquals(0xFFFFL, onesComplementSum(packet, 20, udpLength, pseudoHeader))
            assertArrayEquals(query, packet.copyOfRange(28, packet.size))
        }
    }

    @Test
    fun responsesAreRecognisedByIdAndTheResponseFlag() {
        val query = DnsProbePacket.buildQuery(0xBEEF)
        val response = query.copyOf().also { it[2] = (it[2].toInt() or 0x80).toByte() }
        assertTrue(DnsProbePacket.isResponse(response, response.size, 0xBEEF))
        assertTrue(DnsProbePacket.isResponse(response.copyOf(512), response.size, 0xBEEF))
        assertFalse(DnsProbePacket.isResponse(query, query.size, 0xBEEF))
        assertFalse(DnsProbePacket.isResponse(response, response.size, 0xBEEE))
        assertFalse(DnsProbePacket.isResponse(response, 11, 0xBEEF))
    }
}
