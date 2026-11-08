/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import java.security.SecureRandom

/**
 * Minimal WireGuard responder written from the protocol description (whitepaper, section 5.4),
 * so the initiator is checked against an independent implementation of the other side.
 */
internal class TestWireGuardResponder(
    private val staticPrivate: ByteArray,
    private val random: SecureRandom = SecureRandom(),
) {
    val staticPublic: ByteArray = WireGuardCrypto.publicKey(staticPrivate)

    private val mac1Key = WireGuardCrypto.hash(LABEL_MAC1, staticPublic)
    private val cookieKey = WireGuardCrypto.hash(LABEL_COOKIE, staticPublic)

    class Initiation(
        val message: ByteArray,
        val messageType: Int,
        val senderIndex: Int,
        val ephemeral: ByteArray,
        val initiatorStatic: ByteArray,
        val timestamp: ByteArray,
        val chainKey: ByteArray,
        val hash: ByteArray,
    ) {
        val mac1: ByteArray get() = message.copyOfRange(MAC1_OFFSET, MAC2_OFFSET)
        val mac2: ByteArray get() = message.copyOfRange(MAC2_OFFSET, INITIATION_SIZE)
    }

    class Response(val message: ByteArray, val keys: TransportKeys)

    /** Returns null unless [message] is a valid initiation for this responder's static key. */
    fun consumeInitiation(message: ByteArray): Initiation? {
        if (message.size != INITIATION_SIZE) return null
        val mac1 = message.copyOfRange(MAC1_OFFSET, MAC2_OFFSET)
        if (!WireGuardCrypto.mac(mac1Key, message, MAC1_OFFSET).contentEquals(mac1)) return null
        val ephemeral = message.copyOfRange(8, 40)
        var chainKey = WireGuardCrypto.kdf(CHAIN_KEY, ephemeral, 1)[0]
        var hash = WireGuardCrypto.hash(WireGuardCrypto.hash(HASH, staticPublic), ephemeral)
        val ephemeralStatic = WireGuardCrypto.dh(staticPrivate, ephemeral) ?: return null
        var derived = WireGuardCrypto.kdf(chainKey, ephemeralStatic, 2)
        chainKey = derived[0]
        val initiatorStatic = WireGuardCrypto.aeadOpen(derived[1], 0, message, 40, 48, hash) ?: return null
        hash = WireGuardCrypto.hash(hash, message.copyOfRange(40, 88))
        val staticStatic = WireGuardCrypto.dh(staticPrivate, initiatorStatic) ?: return null
        derived = WireGuardCrypto.kdf(chainKey, staticStatic, 2)
        chainKey = derived[0]
        val timestamp = WireGuardCrypto.aeadOpen(derived[1], 0, message, 88, 28, hash) ?: return null
        hash = WireGuardCrypto.hash(hash, message.copyOfRange(88, MAC1_OFFSET))
        return Initiation(
            message.copyOf(),
            message.readIntLe(0),
            message.readIntLe(4),
            ephemeral,
            initiatorStatic,
            timestamp,
            chainKey,
            hash,
        )
    }

    fun hasValidMac2(initiation: Initiation, cookie: ByteArray): Boolean =
        WireGuardCrypto.mac(cookie, initiation.message, MAC2_OFFSET).contentEquals(initiation.mac2)

    fun createResponse(initiation: Initiation, senderIndex: Int, messageType: Int = 2): Response {
        val ephemeralPrivate = WireGuardCrypto.generatePrivateKey(random)
        val ephemeralPublic = WireGuardCrypto.publicKey(ephemeralPrivate)
        val message = ByteArray(RESPONSE_SIZE)
        message.writeIntLe(0, messageType)
        message.writeIntLe(4, senderIndex)
        message.writeIntLe(8, initiation.senderIndex)
        ephemeralPublic.copyInto(message, 12)
        var hash = WireGuardCrypto.hash(initiation.hash, ephemeralPublic)
        var chainKey = WireGuardCrypto.kdf(initiation.chainKey, ephemeralPublic, 1)[0]
        val ephemeralEphemeral = checkNotNull(WireGuardCrypto.dh(ephemeralPrivate, initiation.ephemeral))
        chainKey = WireGuardCrypto.kdf(chainKey, ephemeralEphemeral, 1)[0]
        val ephemeralStatic = checkNotNull(WireGuardCrypto.dh(ephemeralPrivate, initiation.initiatorStatic))
        chainKey = WireGuardCrypto.kdf(chainKey, ephemeralStatic, 1)[0]
        val derived = WireGuardCrypto.kdf(chainKey, ByteArray(32), 3)
        chainKey = derived[0]
        hash = WireGuardCrypto.hash(hash, derived[1])
        WireGuardCrypto.aeadSeal(derived[2], 0, ByteArray(0), hash).copyInto(message, 44)
        val initiatorMac1Key = WireGuardCrypto.hash(LABEL_MAC1, initiation.initiatorStatic)
        WireGuardCrypto.mac(initiatorMac1Key, message, 60).copyInto(message, 60)
        val transport = WireGuardCrypto.kdf(chainKey, ByteArray(0), 2)
        // The responder receives with the first transport key and sends with the second.
        val keys = TransportKeys(senderIndex, initiation.senderIndex, sendKey = transport[1], receiveKey = transport[0])
        return Response(message, keys)
    }

    fun createCookieReply(initiation: Initiation, cookie: ByteArray, messageType: Int = 3): ByteArray {
        val message = ByteArray(COOKIE_REPLY_SIZE)
        message.writeIntLe(0, messageType)
        message.writeIntLe(4, initiation.senderIndex)
        val nonce = ByteArray(24).also(random::nextBytes)
        nonce.copyInto(message, 8)
        WireGuardCrypto.xaeadSeal(cookieKey, nonce, cookie, initiation.mac1).copyInto(message, 32)
        return message
    }

    private companion object {
        const val INITIATION_SIZE = 148
        const val RESPONSE_SIZE = 92
        const val COOKIE_REPLY_SIZE = 64
        const val MAC1_OFFSET = 116
        const val MAC2_OFFSET = 132
        val LABEL_MAC1: ByteArray = "mac1----".toByteArray(Charsets.US_ASCII)
        val LABEL_COOKIE: ByteArray = "cookie--".toByteArray(Charsets.US_ASCII)
        val CHAIN_KEY: ByteArray = WireGuardCrypto.hash("Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray(Charsets.US_ASCII))
        val HASH: ByteArray = WireGuardCrypto.hash(CHAIN_KEY, "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray(Charsets.US_ASCII))
    }
}
