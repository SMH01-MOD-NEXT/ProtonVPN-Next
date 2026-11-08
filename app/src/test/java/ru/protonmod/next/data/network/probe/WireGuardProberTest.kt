/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.security.SecureRandom

/** End-to-end probes against in-process servers on the loopback interface. */
class WireGuardProberTest {

    private val random = SecureRandom()
    private val clientPrivate = WireGuardCrypto.generatePrivateKey(random)
    private val prober = WireGuardProber(clientPrivate, random, HandshakeTimestampSource()) { DatagramSocket() }
    private val unknownServerKey = WireGuardCrypto.publicKey(WireGuardCrypto.generatePrivateKey(random))
    private val loopback = InetAddress.getLoopbackAddress()
    private val closeables = ArrayList<AutoCloseable>()

    @After
    fun tearDown() {
        closeables.forEach { it.close() }
    }

    private fun <T : AutoCloseable> T.closeAfterTest(): T = also { closeables += it }

    private fun config(
        ports: List<Int>,
        obfuscation: AwgObfuscation = AwgObfuscation.STANDARD,
        dataCheck: WireGuardProber.DataCheck? = null,
        attempts: Int = 2,
    ) = WireGuardProber.Config(ports, 300, attempts, obfuscation, 1420, dataCheck)

    private fun probe(key: ByteArray, address: InetAddress, config: WireGuardProber.Config) =
        runBlocking { prober.probe(key, address, config) }

    private fun probe(server: TestWireGuardServer, config: WireGuardProber.Config) =
        probe(server.publicKey, server.address, config)

    private fun WireGuardProber.Outcome.verified(): WireGuardProber.Outcome.Verified =
        this as? WireGuardProber.Outcome.Verified ?: throw AssertionError("Expected a verified handshake, got " + this)

    /** The server handles datagrams on its own thread, so the final keepalive may land after probe() returns. */
    private fun awaitKeepalives(server: TestWireGuardServer, expected: Int): Int {
        val deadline = System.nanoTime() + KEEPALIVE_WAIT_NANOS
        while (server.keepalives < expected && System.nanoTime() < deadline) Thread.sleep(5)
        return server.keepalives
    }

    @Test
    fun plainWireGuardHandshakeIsVerified() {
        val server = TestWireGuardServer().closeAfterTest()
        val outcome = probe(server, config(server.ports)).verified()
        assertEquals(server.ports, outcome.ports)
        assertEquals(server.ports.single(), outcome.bestPort)
        assertEquals(WireGuardProber.DataPlane.NOT_CHECKED, outcome.dataPlane)
        assertTrue(outcome.latencyMs >= 0)
        assertEquals(1, server.handshakes)
    }

    @Test
    fun amneziaWgProbeMatchesTheServerFramingAndPassesData() {
        val server = TestWireGuardServer(AWG_SETTINGS).closeAfterTest()
        val obfuscation = AwgObfuscation.fromSettings(AWG_SETTINGS)
        val outcome = probe(server, config(server.ports, obfuscation, DATA_CHECK)).verified()
        assertEquals(WireGuardProber.DataPlane.VERIFIED, outcome.dataPlane)
        assertEquals(1, server.handshakes)
        // I1 and three junk packets precede every initiation.
        assertTrue("decoys: " + server.decoys, server.decoys >= 4)
        assertTrue(server.initiationTypes.isNotEmpty())
        assertTrue(server.initiationTypes.all { it in 100_000L..200_000L })
        val query = server.dnsQueries.first()
        assertEquals(DATA_CHECK.tunnelAddress, query.source)
        assertEquals(DATA_CHECK.dnsAddress, query.destination)
        // The verified reply is acknowledged, so the server does not retry handshakes afterwards.
        assertEquals(1, awaitKeepalives(server, 1))
    }

    @Test
    fun verifiedDataPlaneIsAcknowledgedWithAPaddedKeepalive() {
        val settings = AWG_SETTINGS.copy(contentPaddingAddition = "8-24")
        val server = TestWireGuardServer(settings).closeAfterTest()
        val outcome = probe(server, config(server.ports, AwgObfuscation.fromSettings(settings), DATA_CHECK)).verified()
        assertEquals(WireGuardProber.DataPlane.VERIFIED, outcome.dataPlane)
        assertEquals(1, awaitKeepalives(server, 1))
    }

    @Test
    fun cookieReplyIsAnsweredWithMac2() {
        val server = TestWireGuardServer(cookies = TestWireGuardServer.CookieMode.UNTIL_MAC2).closeAfterTest()
        assertEquals(server.ports, probe(server, config(server.ports)).verified().ports)
        assertEquals(1, server.cookieReplies)
        assertEquals(1, server.validMac2)
        assertEquals(1, server.handshakes)
    }

    @Test
    fun serverThatOnlySendsCookiesIsBusy() {
        val server = TestWireGuardServer(cookies = TestWireGuardServer.CookieMode.ALWAYS).closeAfterTest()
        assertEquals(WireGuardProber.Outcome.CookieOnly, probe(server, config(server.ports)))
        assertTrue(server.validMac2 >= 1)
        assertEquals(0, server.handshakes)
    }

    @Test
    fun silentServerGivesNoResponse() {
        val silent = LoopbackUdpServer(1) { _, _ -> emptyList() }.closeAfterTest()
        assertEquals(WireGuardProber.Outcome.NoResponse, probe(unknownServerKey, silent.address, config(silent.ports)))
    }

    @Test
    fun forgedResponsesAreNeverVerified() {
        val forger = LoopbackUdpServer(1) { _, datagram ->
            val forged = ByteArray(WireGuardHandshake.RESPONSE_SIZE).also(random::nextBytes)
            forged.writeIntLe(0, 2)
            forged.writeIntLe(8, datagram.readIntLe(4))
            listOf(forged)
        }.closeAfterTest()
        assertEquals(WireGuardProber.Outcome.InvalidResponse, probe(unknownServerKey, forger.address, config(forger.ports)))
    }

    @Test
    fun junkFromTheServerAddressIsAnInvalidResponse() {
        val noisy = LoopbackUdpServer(1) { _, _ -> listOf(ByteArray(7).also(random::nextBytes)) }.closeAfterTest()
        assertEquals(WireGuardProber.Outcome.InvalidResponse, probe(unknownServerKey, noisy.address, config(noisy.ports)))
    }

    @Test
    fun blockedDataPlaneIsConfirmedWithAFreshHandshake() {
        val server = TestWireGuardServer(answerData = false).closeAfterTest()
        val outcome = probe(server, config(server.ports, dataCheck = DATA_CHECK)).verified()
        assertEquals(WireGuardProber.DataPlane.BLOCKED, outcome.dataPlane)
        assertEquals(2, server.handshakes)
        assertEquals(0, server.replays)
        assertTrue(server.dnsQueries.isNotEmpty())
        // Nothing was verified, so nothing is acknowledged.
        assertEquals(0, server.keepalives)
    }

    @Test
    fun dataPlaneIsInconclusiveWhenTheServerStopsAnswering() {
        val server = TestWireGuardServer(handshakeLimit = 1, answerData = false).closeAfterTest()
        val outcome = probe(server, config(server.ports, dataCheck = DATA_CHECK)).verified()
        assertEquals(WireGuardProber.DataPlane.INCONCLUSIVE, outcome.dataPlane)
    }

    @Test
    fun everyPortIsTriedAndTheFastestWins() {
        val server = TestWireGuardServer(portCount = 2, replyDelayMs = { if (it == 0) 150L else 0L }).closeAfterTest()
        val outcome = probe(server, config(server.ports, dataCheck = DATA_CHECK)).verified()
        assertEquals(server.ports.sorted(), outcome.ports)
        assertEquals(server.ports[1], outcome.bestPort)
        assertEquals(WireGuardProber.DataPlane.VERIFIED, outcome.dataPlane)
        // The newest session is the one the server kept, so no extra handshake was needed.
        assertEquals(2, server.handshakes)
        assertEquals(0, server.replays)
        assertEquals(1, awaitKeepalives(server, 1))
    }

    @Test
    fun lowOrderServerKeyIsRejected() {
        assertEquals(WireGuardProber.Outcome.InvalidKey, probe(ByteArray(32), loopback, config(listOf(9))))
    }

    @Test
    fun socketFailureIsReported() {
        val failing = WireGuardProber(clientPrivate, random, HandshakeTimestampSource()) { throw SocketException("no network") }
        val outcome = runBlocking { failing.probe(unknownServerKey, loopback, config(listOf(9))) }
        assertEquals(WireGuardProber.Outcome.Failed("SocketException"), outcome)
    }

    @Test
    fun invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { probe(unknownServerKey, loopback, config(emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { probe(unknownServerKey, loopback, config(listOf(9), attempts = 0)) }
    }

    private companion object {
        const val KEEPALIVE_WAIT_NANOS = 2_000_000_000L
        val DATA_CHECK = WireGuardProber.DataCheck(ipv4Address(10, 2, 0, 2), ipv4Address(10, 2, 0, 1))
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
            headerProtectionKey = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        )
    }
}
