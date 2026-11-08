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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class WireGuardHandshakeTest {

    private val random = SecureRandom()
    private val responder = TestWireGuardResponder(WireGuardCrypto.generatePrivateKey(random), random)
    private val clientPrivate = WireGuardCrypto.generatePrivateKey(random)
    private val keys = WireGuardPeerKeys(clientPrivate, responder.staticPublic)
    private val timestamp = WireGuardCrypto.tai64n(1_700_000_000_000_000_000L)

    private fun initiate(senderIndex: Int = SENDER_INDEX, messageType: Int = 1, cookie: ByteArray? = null) =
        checkNotNull(WireGuardHandshake.createInitiation(keys, senderIndex, timestamp, messageType, cookie, random))

    private fun accept(message: ByteArray) = checkNotNull(responder.consumeInitiation(message)) { "Initiation rejected" }

    private fun handshake(): Pair<TransportKeys, TransportKeys> {
        val (message, state) = initiate()
        val response = responder.createResponse(accept(message), RESPONDER_INDEX)
        val client = checkNotNull(WireGuardHandshake.consumeResponse(keys, state, response.message)) { "Response rejected" }
        return client to response.keys
    }

    private fun ByteArray.flipped(offset: Int) = copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }

    @Test
    fun noiseConstantsMatchWireGuard() {
        assertEquals(EXPECTED_CHAIN_KEY, WireGuardHandshake.INITIAL_CHAIN_KEY.toHex())
        assertEquals(EXPECTED_HASH, WireGuardHandshake.INITIAL_HASH.toHex())
    }

    @Test
    fun initiationIsAcceptedByAnIndependentResponder() {
        val (message, state) = initiate()
        assertEquals(WireGuardHandshake.INITIATION_SIZE, message.size)
        assertEquals(WireGuardHandshake.MESSAGE_INITIATION, message.uint32Le(0))
        assertEquals(SENDER_INDEX, message.readIntLe(4))
        val initiation = accept(message)
        assertEquals(SENDER_INDEX, initiation.senderIndex)
        assertArrayEquals(keys.staticPublic, initiation.initiatorStatic)
        assertArrayEquals(timestamp, initiation.timestamp)
        assertArrayEquals(ByteArray(WireGuardCrypto.MAC_SIZE), initiation.mac2)
        assertEquals(SENDER_INDEX, state.senderIndex)
        assertArrayEquals(initiation.mac1, state.mac1)
    }

    @Test
    fun initiationCarriesTheRequestedMessageType() {
        val (message, _) = initiate(messageType = 0xDEADBEEFL.toInt())
        assertEquals(0xDEADBEEFL, message.uint32Le(0))
        assertEquals(0xDEADBEEFL.toInt(), accept(message).messageType)
    }

    @Test
    fun everyInitiationUsesAFreshEphemeralKey() {
        val first = initiate().first
        val second = initiate().first
        assertFalse(first.copyOfRange(8, 40).contentEquals(second.copyOfRange(8, 40)))
    }

    @Test
    fun mac1CoversEveryFieldBeforeIt() {
        val (message, _) = initiate()
        for (offset in listOf(0, 4, 8, 39, 40, 87, 88, 115, 116, 131)) {
            assertNull("offset $offset", responder.consumeInitiation(message.flipped(offset)))
        }
    }

    @Test
    fun responseCompletesTheHandshake() {
        val (client, server) = handshake()
        assertEquals(SENDER_INDEX, client.localIndex)
        assertEquals(RESPONDER_INDEX, client.remoteIndex)
        assertArrayEquals(server.receiveKey, client.sendKey)
        assertArrayEquals(server.sendKey, client.receiveKey)
        assertFalse(client.sendKey.contentEquals(client.receiveKey))
    }

    @Test
    fun transportRoundTripsInBothDirections() {
        val (client, server) = handshake()
        val payload = ByteArray(57).also(random::nextBytes)
        val outbound = WireGuardHandshake.sealTransport(client, 0, 4, payload, padding = 7)
        assertEquals(WireGuardHandshake.TRANSPORT_HEADER_SIZE + 64 + WireGuardCrypto.AEAD_TAG_SIZE, outbound.size)
        assertEquals(WireGuardHandshake.MESSAGE_TRANSPORT, outbound.uint32Le(0))
        assertEquals(RESPONDER_INDEX, outbound.readIntLe(4))
        assertEquals(0L, outbound.readLongLe(8))
        assertArrayEquals(payload + ByteArray(7), WireGuardHandshake.openTransport(server, outbound))
        val inbound = WireGuardHandshake.sealTransport(server, 5, 4, payload, padding = 0)
        assertEquals(5L, inbound.readLongLe(8))
        assertArrayEquals(payload, WireGuardHandshake.openTransport(client, inbound))
    }

    @Test
    fun transportRejectsForeignAndDamagedMessages() {
        val (client, server) = handshake()
        val message = WireGuardHandshake.sealTransport(server, 1, 4, ByteArray(10), padding = 0)
        assertNotNull(WireGuardHandshake.openTransport(client, message))
        assertNull(WireGuardHandshake.openTransport(client, message.copyOf().also { it.writeIntLe(4, SENDER_INDEX + 1) }))
        assertNull(WireGuardHandshake.openTransport(client, message.copyOf().also { it.writeLongLe(8, 2) }))
        assertNull(WireGuardHandshake.openTransport(client, message.flipped(20)))
        assertNull(WireGuardHandshake.openTransport(client, message.copyOf(WireGuardHandshake.TRANSPORT_MIN_SIZE - 1)))
        assertNull(WireGuardHandshake.openTransport(server, message))
    }

    @Test
    fun negativeTransportPaddingIsRejected() {
        val (client, _) = handshake()
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardHandshake.sealTransport(client, 0, 4, ByteArray(1), padding = -1)
        }
    }

    @Test
    fun damagedResponsesAreRejected() {
        val (message, state) = initiate()
        val response = responder.createResponse(accept(message), RESPONDER_INDEX).message
        for (offset in listOf(12, 30, 43, 44, 59)) {
            assertNull("offset $offset", WireGuardHandshake.consumeResponse(keys, state, response.flipped(offset)))
        }
        val otherReceiver = response.copyOf().also { it.writeIntLe(8, SENDER_INDEX + 1) }
        assertNull(WireGuardHandshake.consumeResponse(keys, state, otherReceiver))
        assertNull(WireGuardHandshake.consumeResponse(keys, state, response.copyOf(WireGuardHandshake.RESPONSE_SIZE - 1)))
        assertNotNull(WireGuardHandshake.consumeResponse(keys, state, response))
    }

    @Test
    fun responseToAnotherInitiationFailsAuthentication() {
        val (_, firstState) = initiate()
        val (second, _) = initiate()
        val response = responder.createResponse(accept(second), RESPONDER_INDEX).message
        assertNull(WireGuardHandshake.consumeResponse(keys, firstState, response))
    }

    @Test
    fun lowOrderServerKeyMakesTheHandshakeImpossible() {
        val lowOrder = WireGuardPeerKeys(clientPrivate, ByteArray(32))
        assertNull(lowOrder.staticStatic)
        assertNull(WireGuardHandshake.createInitiation(lowOrder, SENDER_INDEX, timestamp, 1, null, random))
    }

    @Test
    fun cookieReplyYieldsTheCookieAndEnablesMac2() {
        val (message, state) = initiate()
        val initiation = accept(message)
        val cookie = ByteArray(16).also(random::nextBytes)
        assertFalse(responder.hasValidMac2(initiation, cookie))
        val reply = responder.createCookieReply(initiation, cookie)
        assertEquals(WireGuardHandshake.COOKIE_REPLY_SIZE, reply.size)
        val decrypted = WireGuardHandshake.consumeCookieReply(keys, state, reply)
        assertArrayEquals(cookie, decrypted)
        val (retry, _) = initiate(cookie = decrypted)
        assertTrue(responder.hasValidMac2(accept(retry), cookie))
    }

    @Test
    fun cookieReplyIsBoundToItsInitiation() {
        val (first, firstState) = initiate()
        val (_, secondState) = initiate()
        val reply = responder.createCookieReply(accept(first), ByteArray(16) { it.toByte() })
        assertNull(WireGuardHandshake.consumeCookieReply(keys, secondState, reply))
        val otherReceiver = reply.copyOf().also { it.writeIntLe(4, SENDER_INDEX + 1) }
        assertNull(WireGuardHandshake.consumeCookieReply(keys, firstState, otherReceiver))
        assertNull(WireGuardHandshake.consumeCookieReply(keys, firstState, reply.flipped(40)))
        assertNull(WireGuardHandshake.consumeCookieReply(keys, firstState, reply.copyOf(WireGuardHandshake.COOKIE_REPLY_SIZE - 1)))
        assertNotNull(WireGuardHandshake.consumeCookieReply(keys, firstState, reply))
    }

    private companion object {
        const val SENDER_INDEX = 0x01020304
        const val RESPONDER_INDEX = 0x0A0B0C0D
        const val EXPECTED_CHAIN_KEY = "60e26daef327efc02ec335e2a025d2d016eb4206f87277f52d38d1988b78cd36"
        const val EXPECTED_HASH = "2211b361081ac566691243db458ad5322d9c6c662293e8b70ee19c65ba079ef3"
    }
}
