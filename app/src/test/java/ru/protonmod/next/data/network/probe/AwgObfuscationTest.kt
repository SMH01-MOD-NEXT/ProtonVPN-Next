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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class AwgObfuscationTest {

    private val random = SecureRandom()

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    /** A WireGuard-shaped message of [size] bytes whose first four bytes carry [type]. */
    private fun message(size: Int, type: Int) = randomBytes(size).also { it.writeIntLe(0, type) }

    private fun obfuscation(settings: AwgObfuscationSettings) = AwgObfuscation.fromSettings(settings)

    private fun ByteArray.ascii() = String(this, Charsets.US_ASCII)

    /** Unwraps [datagram] out of a larger receive buffer, the way the prober does. */
    private fun AwgObfuscation.receive(datagram: ByteArray): AwgObfuscation.Message? {
        val buffer = ByteArray(8_192)
        datagram.copyInto(buffer)
        return unwrap(buffer, datagram.size)
    }

    @Test
    fun typeRangeParsesSingleValuesAndRanges() {
        assertEquals(AwgTypeRange(1, 1), AwgTypeRange.parse("", 1))
        assertEquals(AwgTypeRange(2, 2), AwgTypeRange.parse("   ", 2))
        assertEquals(AwgTypeRange(5, 5), AwgTypeRange.parse("5", 1))
        assertEquals(AwgTypeRange(100, 200), AwgTypeRange.parse(" 100 - 200 ", 1))
        assertEquals(AwgTypeRange(0, 0xFFFFFFFFL), AwgTypeRange.parse("0-4294967295", 1))
    }

    @Test
    fun typeRangeRejectsMalformedSpecs() {
        for (spec in listOf("200-100", "1-2-3", "-1", "4294967296", "abc", "1-", "1.5")) {
            assertThrows(spec, AwgConfigException::class.java) { AwgTypeRange.parse(spec, 1) }
        }
    }

    @Test
    fun typeRangePicksInsideTheRange() {
        val range = AwgTypeRange(0x7FFFFFF0L, 0x80000010L)
        repeat(200) {
            val picked = range.pick(random).toLong() and 0xFFFFFFFFL
            assertTrue("picked " + picked, picked in range)
        }
        assertEquals(7, AwgTypeRange.single(7).pick(random))
        assertTrue(5L in AwgTypeRange(5, 6))
        assertFalse(7L in AwgTypeRange(5, 6))
    }

    @Test
    fun initiationTypesCoverTheWholeH1Range() {
        val awg = obfuscation(AwgObfuscationSettings(h1 = "10-12"))
        assertEquals(setOf(10, 11, 12), (1..300).map { awg.initiationType(random) }.toSet())
    }

    @Test
    fun specialPacketWithoutTagsIsSkipped() {
        assertNull(AwgSpecialPacket.parse(""))
        assertNull(AwgSpecialPacket.parse("no tags here"))
    }

    @Test
    fun specialPacketBuildsEveryTag() {
        val packet = checkNotNull(AwgSpecialPacket.parse("<b 0xc0ffee> <t><dz 2> <rd 3><rc 4><r 5><d><ds>"))
        val bytes = packet.build(random, unixSeconds = 0x01020304L)
        assertEquals(3 + 4 + 2 + 3 + 4 + 5, bytes.size)
        assertEquals("c0ffee", bytes.copyOfRange(0, 3).toHex())
        assertEquals("01020304", bytes.copyOfRange(3, 7).toHex())
        assertEquals("0000", bytes.copyOfRange(7, 9).toHex())
        assertTrue(bytes.copyOfRange(9, 12).ascii().matches(Regex("[0-9]{3}")))
        assertTrue(bytes.copyOfRange(12, 16).ascii().matches(Regex("[a-zA-Z]{4}")))
    }

    @Test
    fun specialPacketToleratesBareHexWhitespaceAndSurroundingText() {
        assertEquals("c0ffee", checkNotNull(AwgSpecialPacket.parse("<b C0FFEE>")).build(random, 0).toHex())
        assertEquals("01", checkNotNull(AwgSpecialPacket.parse("junk <  b   0x01  > more")).build(random, 0).toHex())
        assertEquals(65_507, checkNotNull(AwgSpecialPacket.parse("<dz 65507>")).build(random, 0).size)
    }

    @Test
    fun specialPacketRejectsInvalidTags() {
        val invalid = listOf(
            "<b 0x01",
            "<>",
            "<b 0x>",
            "<b 0xabc>",
            "<b 0xzz>",
            "<r>",
            "<r -1>",
            "<r 65508>",
            "<x 1>",
            "<b 0x01><q>",
        )
        for (spec in invalid) {
            assertThrows(spec, AwgConfigException::class.java) { AwgSpecialPacket.parse(spec) }
        }
    }

    @Test
    fun standardModeIsPlainWireGuard() {
        val standard = AwgObfuscation.STANDARD
        assertEquals(1, standard.initiationType(random))
        assertEquals(4, standard.transportMessageType(random))
        assertTrue(standard.decoys(random, 0).isEmpty())
        val initiation = message(WireGuardHandshake.INITIATION_SIZE, 1)
        assertArrayEquals(initiation, standard.wrapInitiation(initiation, random))
        val transport = message(48, 4)
        assertArrayEquals(transport, standard.wrapTransport(transport, random))
        val defaults = obfuscation(AwgObfuscationSettings())
        assertEquals(1, defaults.initiationType(random))
        assertArrayEquals(initiation, defaults.wrapInitiation(initiation, random))
    }

    @Test
    fun standardUnwrapClassifiesByLengthAndType() {
        val standard = AwgObfuscation.STANDARD
        val response = message(WireGuardHandshake.RESPONSE_SIZE, 2)
        val received = standard.receive(response)
        assertEquals(AwgObfuscation.Kind.RESPONSE, received?.kind)
        assertArrayEquals(response, received?.bytes)
        assertEquals(AwgObfuscation.Kind.COOKIE_REPLY, standard.receive(message(WireGuardHandshake.COOKIE_REPLY_SIZE, 3))?.kind)
        assertEquals(AwgObfuscation.Kind.TRANSPORT, standard.receive(message(WireGuardHandshake.TRANSPORT_MIN_SIZE, 4))?.kind)
        assertEquals(AwgObfuscation.Kind.TRANSPORT, standard.receive(message(WireGuardHandshake.RESPONSE_SIZE, 4))?.kind)
        assertNull(standard.receive(message(WireGuardHandshake.TRANSPORT_MIN_SIZE - 1, 4)))
        assertNull(standard.receive(message(WireGuardHandshake.RESPONSE_SIZE + 1, 2)))
        assertNull(standard.receive(message(WireGuardHandshake.INITIATION_SIZE, 1)))
        assertNull(standard.receive(message(WireGuardHandshake.RESPONSE_SIZE, 5)))
    }

    @Test
    fun standardContentPaddingRoundsUpToSixteenWithinTheMtu() {
        val standard = AwgObfuscation.STANDARD
        val window = AwgObfuscation.DEFAULT_UDP_WINDOW
        assertEquals(0, standard.transportContentPadding(0, MTU, window, random))
        assertEquals(15, standard.transportContentPadding(1, MTU, window, random))
        assertEquals(7, standard.transportContentPadding(57, MTU, window, random))
        assertEquals(0, standard.transportContentPadding(64, MTU, window, random))
        // Never past the MTU: 1415 would round up to 1424.
        assertEquals(5, standard.transportContentPadding(1415, MTU, window, random))
        assertEquals(0, standard.transportContentPadding(MTU, MTU, window, random))
        // Larger packets pad their last MTU-sized unit, as wireguard-go does.
        assertEquals(0, standard.transportContentPadding(1500, MTU, window, random))
        assertEquals(15, standard.transportContentPadding(1501, MTU, window, random))
        // The UDP window only bounds a content padding range.
        assertEquals(7, standard.transportContentPadding(57, MTU, 0, random))
    }

    @Test
    fun contentPaddingAdditionIsDrawnFromItsRangeAndCappedByTheUdpWindow() {
        val padded = obfuscation(AwgObfuscationSettings(contentPaddingAddition = "10-20"))
        val fresh = AwgObfuscation.DEFAULT_UDP_WINDOW
        repeat(100) { assertTrue(padded.transportContentPadding(100, MTU, fresh, random) in 10..20) }
        // A new peer's 500-byte window leaves 500 - 32 - 460 = 8 bytes of room, and none past it.
        assertEquals(8, padded.transportContentPadding(460, MTU, padded.udpWindowAfterSend(fresh, 460), random))
        assertEquals(0, padded.transportContentPadding(1415, MTU, padded.udpWindowAfterSend(fresh, 1415), random))
        assertEquals(0, padded.transportContentPadding(100, MTU, 0, random))
        // Once a full-size datagram went through, the room is the MTU headroom.
        val full = padded.udpWindowAfterSend(fresh, MTU)
        assertEquals(5, padded.transportContentPadding(1415, MTU, full, random))
        assertEquals(0, padded.transportContentPadding(MTU, MTU, full, random))
        assertEquals(33, obfuscation(AwgObfuscationSettings(contentPaddingAddition = "33")).transportContentPadding(100, MTU, fresh, random))
        // "0" keeps the standard rounding.
        assertEquals(7, obfuscation(AwgObfuscationSettings(contentPaddingAddition = "0")).transportContentPadding(57, MTU, fresh, random))
    }

    @Test
    fun udpWindowTracksTheLargestDatagramLikeTheFork() {
        val standard = AwgObfuscation.STANDARD
        val fresh = AwgObfuscation.DEFAULT_UDP_WINDOW
        assertEquals(500, fresh)
        assertEquals(fresh, standard.udpWindowAfterSend(fresh, 100))
        assertEquals(32 + MTU, standard.udpWindowAfterSend(fresh, MTU))
        // A received payload includes the sender's content padding; the tag is not counted.
        assertEquals(16 + MTU, standard.udpWindowAfterReceive(fresh, MTU))
        val withS4 = obfuscation(AwgObfuscationSettings(s4 = 24))
        assertEquals(24 + 32 + MTU, withS4.udpWindowAfterSend(fresh, MTU))
        assertEquals(24 + 16 + MTU, withS4.udpWindowAfterReceive(fresh, MTU))
        // The window never shrinks.
        assertEquals(2_000, standard.udpWindowAfterSend(2_000, 100))
        assertEquals(2_000, standard.udpWindowAfterReceive(2_000, 100))
    }

    @Test
    fun contentPaddingRejectsInvalidInput() {
        val window = AwgObfuscation.DEFAULT_UDP_WINDOW
        assertThrows(IllegalArgumentException::class.java) { AwgObfuscation.STANDARD.transportContentPadding(-1, MTU, window, random) }
        assertThrows(IllegalArgumentException::class.java) { AwgObfuscation.STANDARD.transportContentPadding(10, 0, window, random) }
        assertThrows(IllegalArgumentException::class.java) { AwgObfuscation.STANDARD.transportContentPadding(10, MTU, -1, random) }
        assertThrows(AwgConfigException::class.java) { obfuscation(AwgObfuscationSettings(contentPaddingAddition = "20-10")) }
    }

    @Test
    fun settingsRejectOutOfRangeValues() {
        val invalid = listOf(
            AwgObfuscationSettings(jc = -1),
            AwgObfuscationSettings(jmin = -1),
            AwgObfuscationSettings(s3 = -1),
            AwgObfuscationSettings(jmax = 65_508),
            AwgObfuscationSettings(s1 = 65_001),
            AwgObfuscationSettings(h2 = "9-1"),
            AwgObfuscationSettings(i2 = "<b 0x1>"),
            AwgObfuscationSettings(headerProtectionKey = "abcd"),
            AwgObfuscationSettings(headerProtectionKey = "not a key"),
            // Header protection takes its nonce from the padding, so every S must cover it.
            AwgObfuscationSettings(headerProtectionKey = KEY_HEX, s1 = 12, s2 = 12, s3 = 12, s4 = 11),
        )
        for (settings in invalid) {
            assertThrows(settings.toString(), AwgConfigException::class.java) { obfuscation(settings) }
        }
    }

    @Test
    fun allZeroHeaderKeyDisablesHeaderProtection() {
        // Accepted even with S below the nonce size, because protection is off.
        val awg = obfuscation(AwgObfuscationSettings(headerProtectionKey = "00".repeat(32), s1 = 4))
        val initiation = message(WireGuardHandshake.INITIATION_SIZE, 1)
        val datagram = awg.wrapInitiation(initiation, random)
        assertEquals(4 + initiation.size, datagram.size)
        assertArrayEquals(initiation, datagram.copyOfRange(4, datagram.size))
    }

    @Test
    fun headerKeyMayBeBase64() {
        val base64 = Base64.getEncoder().encodeToString(hexBytes(KEY_HEX))
        val settings = AWG_SETTINGS.copy(headerProtectionKey = base64)
        val initiation = message(WireGuardHandshake.INITIATION_SIZE, 150_000)
        val datagram = obfuscation(settings).wrapInitiation(initiation, random)
        assertArrayEquals(initiation, TestAwgFraming(AWG_SETTINGS, random).unwrapInitiation(datagram))
    }

    @Test
    fun initiationIsPaddedAndProtected() {
        val awg = obfuscation(AWG_SETTINGS)
        val framing = TestAwgFraming(AWG_SETTINGS, random)
        repeat(20) {
            val initiation = message(WireGuardHandshake.INITIATION_SIZE, awg.initiationType(random))
            assertTrue(initiation.uint32Le(0) in 100_000L..200_000L)
            val datagram = awg.wrapInitiation(initiation, random)
            assertEquals(AWG_SETTINGS.s1 + WireGuardHandshake.INITIATION_SIZE, datagram.size)
            assertFalse(datagram.copyOfRange(AWG_SETTINGS.s1, datagram.size).contentEquals(initiation))
            assertArrayEquals(initiation, framing.unwrapInitiation(datagram))
        }
    }

    @Test
    fun transportProtectsOnlyTheHeader() {
        val awg = obfuscation(AWG_SETTINGS)
        val transport = message(80, awg.transportMessageType(random))
        val datagram = awg.wrapTransport(transport, random)
        val s4 = AWG_SETTINGS.s4
        val header = WireGuardHandshake.TRANSPORT_HEADER_SIZE
        assertEquals(s4 + transport.size, datagram.size)
        assertFalse(datagram.copyOfRange(s4, s4 + header).contentEquals(transport.copyOf(header)))
        assertArrayEquals(transport.copyOfRange(header, transport.size), datagram.copyOfRange(s4 + header, datagram.size))
        assertArrayEquals(transport, TestAwgFraming(AWG_SETTINGS, random).unwrapTransport(datagram))
    }

    @Test
    fun serverMessagesAreUnwrappedThroughPaddingTypesAndProtection() {
        val awg = obfuscation(AWG_SETTINGS)
        val framing = TestAwgFraming(AWG_SETTINGS, random)
        val expected = listOf(
            AwgObfuscation.Kind.RESPONSE to message(WireGuardHandshake.RESPONSE_SIZE, framing.responseType),
            AwgObfuscation.Kind.COOKIE_REPLY to message(WireGuardHandshake.COOKIE_REPLY_SIZE, framing.cookieType),
            AwgObfuscation.Kind.TRANSPORT to message(64, framing.transportType),
        )
        for ((kind, bytes) in expected) {
            val datagram = when (kind) {
                AwgObfuscation.Kind.RESPONSE -> framing.wrapResponse(bytes)
                AwgObfuscation.Kind.COOKIE_REPLY -> framing.wrapCookieReply(bytes)
                AwgObfuscation.Kind.TRANSPORT -> framing.wrapTransport(bytes)
            }
            val received = awg.receive(datagram)
            assertEquals(kind, received?.kind)
            assertArrayEquals(bytes, received?.bytes)
        }
    }

    @Test
    fun unwrapRejectsForeignTraffic() {
        val awg = obfuscation(AWG_SETTINGS)
        val framing = TestAwgFraming(AWG_SETTINGS, random)
        // Plain WireGuard is not accepted by an obfuscated client.
        assertNull(awg.receive(message(WireGuardHandshake.RESPONSE_SIZE, 2)))
        // A message type outside H2.
        assertNull(awg.receive(framing.wrapResponse(message(WireGuardHandshake.RESPONSE_SIZE, 2))))
        // Another header protection key.
        val stranger = TestAwgFraming(AWG_SETTINGS.copy(headerProtectionKey = "11".repeat(32)), random)
        assertNull(awg.receive(stranger.wrapResponse(message(WireGuardHandshake.RESPONSE_SIZE, framing.responseType))))
        // Shorter than the header protection nonce.
        assertNull(awg.receive(randomBytes(11)))
    }

    @Test
    fun decoysSendSpecialPacketsThenJunk() {
        val awg = obfuscation(
            AwgObfuscationSettings(jc = 3, jmin = 10, jmax = 20, i1 = "<b 0x01020304>", i2 = "   ", i3 = "<dz 3>", i4 = "plain text"),
        )
        val decoys = awg.decoys(random, 0)
        assertEquals(5, decoys.size)
        assertEquals("01020304", decoys[0].toHex())
        assertEquals("000000", decoys[1].toHex())
        decoys.drop(2).forEach { assertTrue("junk of " + it.size + " bytes", it.size in 10 until 20) }
        val fixed = obfuscation(AwgObfuscationSettings(jc = 4, jmin = 33, jmax = 33))
        assertEquals(listOf(33, 33, 33, 33), fixed.decoys(random, 0).map { it.size })
    }

    private companion object {
        const val MTU = 1420
        const val KEY_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val AWG_SETTINGS = AwgObfuscationSettings(
            jc = 3,
            jmin = 40,
            jmax = 70,
            s1 = 20,
            s2 = 24,
            s3 = 28,
            s4 = 16,
            h1 = "100000-200000",
            h2 = "300000",
            h3 = "400000-400010",
            h4 = "500000-500100",
            i1 = "<b 0xc0ffee><r 8>",
            headerProtectionKey = KEY_HEX,
        )
    }
}
