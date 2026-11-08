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
 * Static key material for one (account, server) pair.
 *
 * Security-sensitive: [staticPrivate] is the account's WireGuard private key. The probe has to
 * use exactly the key of the real tunnel, otherwise Proton servers drop the initiation and every
 * server would look blocked.
 */
internal class WireGuardPeerKeys(
    val staticPrivate: ByteArray,
    val remoteStatic: ByteArray,
) {
    val staticPublic: ByteArray = WireGuardCrypto.publicKey(staticPrivate)
    val staticStatic: ByteArray? = WireGuardCrypto.dh(staticPrivate, remoteStatic)
    val mac1Key: ByteArray = WireGuardCrypto.hash(WireGuardHandshake.LABEL_MAC1, remoteStatic)
    val cookieKey: ByteArray = WireGuardCrypto.hash(WireGuardHandshake.LABEL_COOKIE, remoteStatic)
    val initialHash: ByteArray = WireGuardCrypto.hash(WireGuardHandshake.INITIAL_HASH, remoteStatic)
}

/** Initiator state kept between sending an initiation and processing the reply. */
internal class InitiatorState(
    val senderIndex: Int,
    val ephemeralPrivate: ByteArray,
    val chainKey: ByteArray,
    val hash: ByteArray,
    val mac1: ByteArray,
)

/** Session keys derived from a completed handshake. */
internal class TransportKeys(
    val localIndex: Int,
    val remoteIndex: Int,
    val sendKey: ByteArray,
    val receiveKey: ByteArray,
)

/**
 * WireGuard Noise_IKpsk2 initiator, byte-compatible with wireguard-go and amneziawg-go.
 *
 * A handshake response that decrypts correctly can only be produced by the holder of the server's
 * private key, so a censor cannot fake an "available" verdict.
 */
internal object WireGuardHandshake {
    const val INITIATION_SIZE = 148
    const val RESPONSE_SIZE = 92
    const val COOKIE_REPLY_SIZE = 64
    const val TRANSPORT_HEADER_SIZE = 16
    const val TRANSPORT_MIN_SIZE = TRANSPORT_HEADER_SIZE + WireGuardCrypto.AEAD_TAG_SIZE

    const val MESSAGE_INITIATION = 1L
    const val MESSAGE_RESPONSE = 2L
    const val MESSAGE_COOKIE_REPLY = 3L
    const val MESSAGE_TRANSPORT = 4L

    private const val MAC1_OFFSET = 116
    private const val MAC2_OFFSET = 132

    val LABEL_MAC1: ByteArray = "mac1----".toByteArray(Charsets.US_ASCII)
    val LABEL_COOKIE: ByteArray = "cookie--".toByteArray(Charsets.US_ASCII)
    val INITIAL_CHAIN_KEY: ByteArray =
        WireGuardCrypto.hash("Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray(Charsets.US_ASCII))
    val INITIAL_HASH: ByteArray =
        WireGuardCrypto.hash(INITIAL_CHAIN_KEY, "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray(Charsets.US_ASCII))
    private val ZERO_PRESHARED_KEY = ByteArray(WireGuardCrypto.KEY_SIZE)

    /**
     * Builds a handshake initiation with the given message type (1, or a value from the AmneziaWG
     * H1 range) and, after a cookie reply, mac2. Returns null when the server key is unusable.
     */
    fun createInitiation(
        keys: WireGuardPeerKeys,
        senderIndex: Int,
        timestamp: ByteArray,
        messageType: Int,
        cookie: ByteArray?,
        random: SecureRandom,
    ): Pair<ByteArray, InitiatorState>? {
        val staticStatic = keys.staticStatic ?: return null
        val message = ByteArray(INITIATION_SIZE)
        message.writeIntLe(0, messageType)
        message.writeIntLe(4, senderIndex)

        val ephemeralPrivate = WireGuardCrypto.generatePrivateKey(random)
        val ephemeralPublic = WireGuardCrypto.publicKey(ephemeralPrivate)
        ephemeralPublic.copyInto(message, 8)
        var chainKey = WireGuardCrypto.kdf(INITIAL_CHAIN_KEY, ephemeralPublic, 1)[0]
        var hash = WireGuardCrypto.hash(keys.initialHash, ephemeralPublic)

        val ephemeralStatic = WireGuardCrypto.dh(ephemeralPrivate, keys.remoteStatic) ?: return null
        var derived = WireGuardCrypto.kdf(chainKey, ephemeralStatic, 2)
        chainKey = derived[0]
        val encryptedStatic = WireGuardCrypto.aeadSeal(derived[1], 0, keys.staticPublic, hash)
        encryptedStatic.copyInto(message, 40)
        hash = WireGuardCrypto.hash(hash, encryptedStatic)

        derived = WireGuardCrypto.kdf(chainKey, staticStatic, 2)
        chainKey = derived[0]
        val encryptedTimestamp = WireGuardCrypto.aeadSeal(derived[1], 0, timestamp, hash)
        encryptedTimestamp.copyInto(message, 88)
        hash = WireGuardCrypto.hash(hash, encryptedTimestamp)

        val mac1 = WireGuardCrypto.mac(keys.mac1Key, message, MAC1_OFFSET)
        mac1.copyInto(message, MAC1_OFFSET)
        if (cookie != null) {
            WireGuardCrypto.mac(cookie, message, MAC2_OFFSET).copyInto(message, MAC2_OFFSET)
        }
        return message to InitiatorState(senderIndex, ephemeralPrivate, chainKey, hash, mac1)
    }

    /** Verifies a handshake response; non-null only if it was produced by the real server. */
    fun consumeResponse(keys: WireGuardPeerKeys, state: InitiatorState, message: ByteArray): TransportKeys? {
        if (message.size != RESPONSE_SIZE) return null
        if (message.readIntLe(8) != state.senderIndex) return null
        val responderIndex = message.readIntLe(4)
        val ephemeral = message.copyOfRange(12, 44)
        var hash = WireGuardCrypto.hash(state.hash, ephemeral)
        var chainKey = WireGuardCrypto.kdf(state.chainKey, ephemeral, 1)[0]
        val ephemeralEphemeral = WireGuardCrypto.dh(state.ephemeralPrivate, ephemeral) ?: return null
        chainKey = WireGuardCrypto.kdf(chainKey, ephemeralEphemeral, 1)[0]
        val staticEphemeral = WireGuardCrypto.dh(keys.staticPrivate, ephemeral) ?: return null
        chainKey = WireGuardCrypto.kdf(chainKey, staticEphemeral, 1)[0]
        val derived = WireGuardCrypto.kdf(chainKey, ZERO_PRESHARED_KEY, 3)
        chainKey = derived[0]
        hash = WireGuardCrypto.hash(hash, derived[1])
        WireGuardCrypto.aeadOpen(derived[2], 0, message, 44, WireGuardCrypto.AEAD_TAG_SIZE, hash) ?: return null
        val transport = WireGuardCrypto.kdf(chainKey, ByteArray(0), 2)
        return TransportKeys(state.senderIndex, responderIndex, transport[0], transport[1])
    }

    /** Decrypts the cookie of a cookie reply (sent by servers under load). */
    fun consumeCookieReply(keys: WireGuardPeerKeys, state: InitiatorState, message: ByteArray): ByteArray? {
        if (message.size != COOKIE_REPLY_SIZE) return null
        if (message.readIntLe(4) != state.senderIndex) return null
        val nonce = message.copyOfRange(8, 32)
        return WireGuardCrypto.xaeadOpen(keys.cookieKey, nonce, message, 32, 32, state.mac1)
    }

    /** Seals [plaintext] followed by [padding] zero bytes; see [AwgObfuscation.transportContentPadding]. */
    fun sealTransport(
        keys: TransportKeys,
        counter: Long,
        messageType: Int,
        plaintext: ByteArray,
        padding: Int,
    ): ByteArray {
        require(padding >= 0) { "Negative transport padding" }
        val encrypted = WireGuardCrypto.aeadSeal(keys.sendKey, counter, plaintext.copyOf(plaintext.size + padding), null)
        val message = ByteArray(TRANSPORT_HEADER_SIZE + encrypted.size)
        message.writeIntLe(0, messageType)
        message.writeIntLe(4, keys.remoteIndex)
        message.writeLongLe(8, counter)
        encrypted.copyInto(message, TRANSPORT_HEADER_SIZE)
        return message
    }

    fun openTransport(keys: TransportKeys, message: ByteArray): ByteArray? {
        if (message.size < TRANSPORT_MIN_SIZE) return null
        if (message.readIntLe(4) != keys.localIndex) return null
        return WireGuardCrypto.aeadOpen(
            keys.receiveKey,
            message.readLongLe(8),
            message,
            TRANSPORT_HEADER_SIZE,
            message.size - TRANSPORT_HEADER_SIZE,
            null,
        )
    }
}
