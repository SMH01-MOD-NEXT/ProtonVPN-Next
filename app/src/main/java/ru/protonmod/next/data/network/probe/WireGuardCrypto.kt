/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Primitives of the WireGuard Noise_IKpsk2 handshake (BLAKE2s, HMAC/HKDF, X25519,
 * ChaCha20-Poly1305, XChaCha20-Poly1305 and TAI64N), byte-compatible with wireguard-go.
 *
 * Security-sensitive: callers pass the account's WireGuard private key. Nothing here logs or
 * stores key material, and intermediate secrets are wiped where the API allows it.
 */
internal object WireGuardCrypto {
    const val KEY_SIZE = 32
    const val HASH_SIZE = 32
    const val MAC_SIZE = 16
    const val AEAD_TAG_SIZE = 16
    const val CHACHA_NONCE_SIZE = 12
    const val XCHACHA_NONCE_SIZE = 24

    /** wireguard-go clears the low 24 bits of the nanoseconds (timestamp whitening). */
    const val TAI64N_GRANULARITY_NANOS = 0x1000000L

    private const val TAI64N_WHITENER_MASK = 0xFFFFFF
    private const val TAI64N_EPOCH_OFFSET = 0x400000000000000aL
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val BLAKE2S_BLOCK_SIZE = 64
    private const val HCHACHA_NONCE_SIZE = 16

    fun hash(vararg parts: ByteArray): ByteArray {
        val digest = Blake2sDigest()
        parts.forEach { digest.update(it, 0, it.size) }
        return ByteArray(HASH_SIZE).also { digest.doFinal(it, 0) }
    }

    /** Keyed BLAKE2s-128 over the first [length] bytes of [data] (WireGuard mac1/mac2). */
    fun mac(key: ByteArray, data: ByteArray, length: Int = data.size): ByteArray {
        val digest = Blake2sDigest(key, MAC_SIZE, null, null)
        digest.update(data, 0, length)
        return ByteArray(MAC_SIZE).also { digest.doFinal(it, 0) }
    }

    /** RFC 2104 HMAC over BLAKE2s-256, identical to wireguard-go's HMAC1/HMAC2. */
    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val block = ByteArray(BLAKE2S_BLOCK_SIZE)
        (if (key.size > BLAKE2S_BLOCK_SIZE) hash(key) else key).copyInto(block)
        val innerPad = ByteArray(BLAKE2S_BLOCK_SIZE) { (block[it].toInt() xor 0x36).toByte() }
        val outerPad = ByteArray(BLAKE2S_BLOCK_SIZE) { (block[it].toInt() xor 0x5c).toByte() }
        block.fill(0)
        val inner = Blake2sDigest()
        inner.update(innerPad, 0, innerPad.size)
        parts.forEach { inner.update(it, 0, it.size) }
        val innerHash = ByteArray(HASH_SIZE).also { inner.doFinal(it, 0) }
        val outer = Blake2sDigest()
        outer.update(outerPad, 0, outerPad.size)
        outer.update(innerHash, 0, innerHash.size)
        innerPad.fill(0)
        outerPad.fill(0)
        return ByteArray(HASH_SIZE).also { outer.doFinal(it, 0) }
    }

    /** WireGuard KDF1/KDF2/KDF3 (HKDF with HMAC-BLAKE2s). */
    fun kdf(chainingKey: ByteArray, input: ByteArray, outputs: Int): List<ByteArray> {
        require(outputs in 1..3) { "KDF supports one to three outputs" }
        val pseudoRandomKey = hmac(chainingKey, input)
        val result = ArrayList<ByteArray>(outputs)
        var previous = ByteArray(0)
        for (index in 1..outputs) {
            previous = hmac(pseudoRandomKey, previous, byteArrayOf(index.toByte()))
            result += previous
        }
        pseudoRandomKey.fill(0)
        return result
    }

    fun generatePrivateKey(random: SecureRandom): ByteArray = X25519PrivateKeyParameters(random).encoded

    fun publicKey(privateKey: ByteArray): ByteArray =
        X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    /** X25519. Returns null for malformed or low-order keys (all-zero result), like wireguard-go. */
    fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray? {
        if (privateKey.size != KEY_SIZE || publicKey.size != KEY_SIZE) return null
        return try {
            val agreement = X25519Agreement()
            agreement.init(X25519PrivateKeyParameters(privateKey, 0))
            ByteArray(KEY_SIZE).also {
                agreement.calculateAgreement(X25519PublicKeyParameters(publicKey, 0), it, 0)
            }
        } catch (_: IllegalStateException) {
            null
        }
    }

    fun aeadSeal(key: ByteArray, counter: Long, plaintext: ByteArray, associatedData: ByteArray?): ByteArray =
        seal(key, counterNonce(counter), plaintext, associatedData)

    fun aeadOpen(
        key: ByteArray,
        counter: Long,
        source: ByteArray,
        offset: Int,
        length: Int,
        associatedData: ByteArray?,
    ): ByteArray? = open(key, counterNonce(counter), source, offset, length, associatedData)

    fun xaeadSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        require(nonce.size == XCHACHA_NONCE_SIZE) { "XChaCha20 needs a 24-byte nonce" }
        val subKey = hChaCha20(key, nonce.copyOfRange(0, HCHACHA_NONCE_SIZE))
        return seal(subKey, extendedNonce(nonce), plaintext, associatedData).also { subKey.fill(0) }
    }

    fun xaeadOpen(
        key: ByteArray,
        nonce: ByteArray,
        source: ByteArray,
        offset: Int,
        length: Int,
        associatedData: ByteArray?,
    ): ByteArray? {
        if (nonce.size != XCHACHA_NONCE_SIZE) return null
        val subKey = hChaCha20(key, nonce.copyOfRange(0, HCHACHA_NONCE_SIZE))
        return open(subKey, extendedNonce(nonce), source, offset, length, associatedData).also { subKey.fill(0) }
    }

    /** XORs [length] bytes of [data] in place with the IETF ChaCha20 keystream from block 0. */
    fun chaCha20Xor(key: ByteArray, nonce: ByteArray, data: ByteArray, offset: Int, length: Int) {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        engine.processBytes(data, offset, length, data, offset)
    }

    /** HChaCha20 sub-key derivation (draft-irtf-cfrg-xchacha, section 2.2). */
    fun hChaCha20(key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == KEY_SIZE && nonce.size == HCHACHA_NONCE_SIZE) { "Invalid HChaCha20 input" }
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (index in 0 until 8) state[4 + index] = key.readIntLe(index * 4)
        for (index in 0 until 4) state[12 + index] = nonce.readIntLe(index * 4)
        repeat(10) {
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }
        val output = ByteArray(KEY_SIZE)
        for (index in 0 until 4) {
            output.writeIntLe(index * 4, state[index])
            output.writeIntLe(16 + index * 4, state[12 + index])
        }
        state.fill(0)
        return output
    }

    /** 12-byte TAI64N label for [unixNanos], whitened exactly like wireguard-go's tai64n.Now(). */
    fun tai64n(unixNanos: Long): ByteArray {
        val seconds = Math.floorDiv(unixNanos, NANOS_PER_SECOND)
        val nanos = Math.floorMod(unixNanos, NANOS_PER_SECOND).toInt() and TAI64N_WHITENER_MASK.inv()
        return ByteArray(12).also {
            it.writeLongBe(0, TAI64N_EPOCH_OFFSET + seconds)
            it.writeIntBe(8, nanos)
        }
    }

    private fun quarterRound(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]
        state[d] = (state[d] xor state[a]).rotateLeft(16)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]).rotateLeft(12)
        state[a] += state[b]
        state[d] = (state[d] xor state[a]).rotateLeft(8)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]).rotateLeft(7)
    }

    private fun counterNonce(counter: Long): ByteArray =
        ByteArray(CHACHA_NONCE_SIZE).also { it.writeLongLe(4, counter) }

    private fun extendedNonce(nonce: ByteArray): ByteArray =
        ByteArray(CHACHA_NONCE_SIZE).also { nonce.copyInto(it, 4, HCHACHA_NONCE_SIZE, XCHACHA_NONCE_SIZE) }

    private fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), AEAD_TAG_SIZE * 8, nonce, associatedData))
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        var written = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        written += cipher.doFinal(output, written)
        return if (written == output.size) output else output.copyOf(written)
    }

    private fun open(
        key: ByteArray,
        nonce: ByteArray,
        source: ByteArray,
        offset: Int,
        length: Int,
        associatedData: ByteArray?,
    ): ByteArray? {
        if (length < AEAD_TAG_SIZE || offset < 0 || offset + length > source.size) return null
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, AEADParameters(KeyParameter(key), AEAD_TAG_SIZE * 8, nonce, associatedData))
            val output = ByteArray(cipher.getOutputSize(length))
            var written = cipher.processBytes(source, offset, length, output, 0)
            written += cipher.doFinal(output, written)
            if (written == output.size) output else output.copyOf(written)
        } catch (_: InvalidCipherTextException) {
            null
        }
    }
}
