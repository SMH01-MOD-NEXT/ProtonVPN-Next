/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class WireGuardCryptoTest {

    private val random = SecureRandom()

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun sequence(size: Int, start: Int = 0) = ByteArray(size) { (start + it).toByte() }

    @Test
    fun blake2sMatchesRfc7693() {
        assertEquals(BLAKE2S_ABC, WireGuardCrypto.hash(ABC).toHex())
    }

    @Test
    fun hashOfPartsEqualsHashOfTheirConcatenation() {
        val first = randomBytes(17)
        val second = randomBytes(70)
        assertArrayEquals(WireGuardCrypto.hash(first + second), WireGuardCrypto.hash(first, second))
    }

    @Test
    fun macIsKeyedBlake2s128OverAPrefix() {
        val data = "WireGuard".toByteArray(Charsets.US_ASCII)
        val mac = WireGuardCrypto.mac(sequence(32), data)
        assertEquals(WireGuardCrypto.MAC_SIZE, mac.size)
        assertEquals(MAC_WIREGUARD, mac.toHex())
        assertArrayEquals(WireGuardCrypto.mac(sequence(32), data.copyOf(4)), WireGuardCrypto.mac(sequence(32), data, 4))
    }

    @Test
    fun hmacMatchesKnownAnswers() {
        assertEquals(HMAC_KEY32, WireGuardCrypto.hmac(sequence(32), ABC).toHex())
        assertEquals(HMAC_KEY100, WireGuardCrypto.hmac(sequence(100), ABC).toHex())
        assertEquals(HMAC_EMPTY_KEY, WireGuardCrypto.hmac(ByteArray(0), ABC).toHex())
    }

    @Test
    fun hmacMatchesBouncyCastleForEveryKeySize() {
        for (keySize in listOf(0, 1, 32, 63, 64, 65, 100)) {
            val key = randomBytes(keySize)
            val first = randomBytes(33)
            val second = randomBytes(7)
            val reference = HMac(Blake2sDigest()).run {
                init(KeyParameter(key))
                update(first, 0, first.size)
                update(second, 0, second.size)
                ByteArray(macSize).also { doFinal(it, 0) }
            }
            assertArrayEquals("key size $keySize", reference, WireGuardCrypto.hmac(key, first, second))
        }
    }

    @Test
    fun kdfMatchesHkdfKnownAnswers() {
        val input = "input".toByteArray(Charsets.US_ASCII)
        val expected = listOf(KDF1, KDF2, KDF3)
        for (outputs in 1..3) {
            assertEquals(expected.take(outputs), WireGuardCrypto.kdf(sequence(32), input, outputs).map { it.toHex() })
        }
    }

    @Test
    fun kdfRejectsUnsupportedOutputCounts() {
        assertThrows(IllegalArgumentException::class.java) { WireGuardCrypto.kdf(ByteArray(32), ByteArray(0), 0) }
        assertThrows(IllegalArgumentException::class.java) { WireGuardCrypto.kdf(ByteArray(32), ByteArray(0), 4) }
    }

    @Test
    fun x25519MatchesRfc7748() {
        val alicePrivate = hexBytes(ALICE_PRIVATE)
        val bobPrivate = hexBytes(BOB_PRIVATE)
        val alicePublic = WireGuardCrypto.publicKey(alicePrivate)
        val bobPublic = WireGuardCrypto.publicKey(bobPrivate)
        assertEquals(ALICE_PUBLIC, alicePublic.toHex())
        assertEquals(BOB_PUBLIC, bobPublic.toHex())
        assertEquals(SHARED_SECRET, WireGuardCrypto.dh(alicePrivate, bobPublic)?.toHex())
        assertEquals(SHARED_SECRET, WireGuardCrypto.dh(bobPrivate, alicePublic)?.toHex())
    }

    @Test
    fun dhRejectsLowOrderAndMalformedKeys() {
        val privateKey = WireGuardCrypto.generatePrivateKey(random)
        assertEquals(WireGuardCrypto.KEY_SIZE, privateKey.size)
        assertNull(WireGuardCrypto.dh(privateKey, ByteArray(32)))
        assertNull(WireGuardCrypto.dh(privateKey, ByteArray(32).also { it[0] = 1 }))
        assertNull(WireGuardCrypto.dh(privateKey, ByteArray(31)))
        assertNull(WireGuardCrypto.dh(ByteArray(33), WireGuardCrypto.publicKey(privateKey)))
    }

    @Test
    fun aeadMatchesJdkChaCha20Poly1305WithTheWireGuardNonce() {
        val key = randomBytes(32)
        val counter = 0x0102030405060708L
        // WireGuard nonce: four zero bytes followed by the little-endian 64-bit counter.
        val nonce = ByteArray(4) + ByteArray(8) { (counter ushr (8 * it)).toByte() }
        for (associatedData in listOf(null, randomBytes(32))) {
            val plaintext = randomBytes(100)
            val sealed = WireGuardCrypto.aeadSeal(key, counter, plaintext, associatedData)
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            if (associatedData != null) cipher.updateAAD(associatedData)
            assertArrayEquals(cipher.doFinal(plaintext), sealed)
            assertArrayEquals(plaintext, WireGuardCrypto.aeadOpen(key, counter, sealed, 0, sealed.size, associatedData))
        }
    }

    @Test
    fun aeadOpenRejectsTamperingAndWrongParameters() {
        val key = randomBytes(32)
        val associatedData = randomBytes(16)
        val plaintext = randomBytes(40)
        val sealed = WireGuardCrypto.aeadSeal(key, 7, plaintext, associatedData)
        assertEquals(plaintext.size + WireGuardCrypto.AEAD_TAG_SIZE, sealed.size)
        val framed = randomBytes(5) + sealed + randomBytes(3)
        assertArrayEquals(plaintext, WireGuardCrypto.aeadOpen(key, 7, framed, 5, sealed.size, associatedData))
        assertNull(WireGuardCrypto.aeadOpen(key, 8, sealed, 0, sealed.size, associatedData))
        assertNull(WireGuardCrypto.aeadOpen(key, 7, sealed, 0, sealed.size, randomBytes(16)))
        assertNull(WireGuardCrypto.aeadOpen(key, 7, sealed, 0, sealed.size, null))
        assertNull(WireGuardCrypto.aeadOpen(randomBytes(32), 7, sealed, 0, sealed.size, associatedData))
        val tampered = sealed.copyOf().also { it[3] = (it[3].toInt() xor 1).toByte() }
        assertNull(WireGuardCrypto.aeadOpen(key, 7, tampered, 0, tampered.size, associatedData))
        assertNull(WireGuardCrypto.aeadOpen(key, 7, sealed, 0, WireGuardCrypto.AEAD_TAG_SIZE - 1, associatedData))
    }

    @Test
    fun emptyPlaintextSealsToABareTag() {
        val key = randomBytes(32)
        val hash = randomBytes(32)
        val sealed = WireGuardCrypto.aeadSeal(key, 0, ByteArray(0), hash)
        assertEquals(WireGuardCrypto.AEAD_TAG_SIZE, sealed.size)
        assertArrayEquals(ByteArray(0), WireGuardCrypto.aeadOpen(key, 0, sealed, 0, sealed.size, hash))
    }

    @Test
    fun xChaCha20Poly1305MatchesDraftVector() {
        val key = sequence(32, start = 0x80)
        val nonce = sequence(24, start = 0x40)
        val associatedData = hexBytes("50515253c0c1c2c3c4c5c6c7")
        val plaintext = SUNSCREEN.toByteArray(Charsets.US_ASCII)
        val sealed = WireGuardCrypto.xaeadSeal(key, nonce, plaintext, associatedData)
        assertEquals(XCHACHA_SEALED, sealed.toHex())
        assertArrayEquals(plaintext, WireGuardCrypto.xaeadOpen(key, nonce, sealed, 0, sealed.size, associatedData))
        sealed[0] = (sealed[0].toInt() xor 0x80).toByte()
        assertNull(WireGuardCrypto.xaeadOpen(key, nonce, sealed, 0, sealed.size, associatedData))
    }

    @Test
    fun xChaCha20RequiresA24ByteNonce() {
        assertThrows(IllegalArgumentException::class.java) {
            WireGuardCrypto.xaeadSeal(ByteArray(32), ByteArray(12), ByteArray(1), null)
        }
        assertNull(WireGuardCrypto.xaeadOpen(ByteArray(32), ByteArray(12), ByteArray(32), 0, 32, null))
    }

    @Test
    fun hChaCha20MatchesDraftVector() {
        val subKey = WireGuardCrypto.hChaCha20(sequence(32), hexBytes("000000090000004a0000000031415927"))
        assertEquals(HCHACHA_SUBKEY, subKey.toHex())
    }

    @Test
    fun chaCha20XorUsesTheIetfKeystreamFromBlockZero() {
        val block = ByteArray(64)
        WireGuardCrypto.chaCha20Xor(ByteArray(32), ByteArray(12), block, 0, block.size)
        assertEquals(CHACHA_BLOCK0, block.toHex())
    }

    @Test
    fun chaCha20XorOnlyTouchesTheGivenSlice() {
        val key = randomBytes(32)
        val nonce = randomBytes(12)
        val original = randomBytes(80)
        val data = original.copyOf()
        WireGuardCrypto.chaCha20Xor(key, nonce, data, 10, 50)
        assertArrayEquals(original.copyOfRange(0, 10), data.copyOfRange(0, 10))
        assertArrayEquals(original.copyOfRange(60, 80), data.copyOfRange(60, 80))
        assertFalse(original.copyOfRange(10, 60).contentEquals(data.copyOfRange(10, 60)))
        WireGuardCrypto.chaCha20Xor(key, nonce, data, 10, 50)
        assertArrayEquals(original, data)
    }

    @Test
    fun tai64nMatchesWireGuardEncoding() {
        assertEquals(TAI64N_ZERO, WireGuardCrypto.tai64n(0).toHex())
        // Nanoseconds are whitened to 2^24 ns steps, like wireguard-go's tai64n.Now().
        assertEquals(TAI64N_SAMPLE, WireGuardCrypto.tai64n(SAMPLE_NANOS).toHex())
        assertArrayEquals(WireGuardCrypto.tai64n(SAMPLE_NANOS), WireGuardCrypto.tai64n(SAMPLE_NANOS + 1))
        val next = WireGuardCrypto.tai64n(SAMPLE_NANOS + WireGuardCrypto.TAI64N_GRANULARITY_NANOS)
        assertTrue(compareUnsigned(next, WireGuardCrypto.tai64n(SAMPLE_NANOS)) > 0)
    }

    private companion object {
        val ABC: ByteArray = "abc".toByteArray(Charsets.US_ASCII)
        const val SAMPLE_NANOS = 1_700_000_000_123_456_789L
        const val BLAKE2S_ABC = "508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982"
        const val MAC_WIREGUARD = "c44bc1a201f2ff374d10eda15244ad8a"
        const val HMAC_KEY32 = "82623be5bc0a391b24dce53e67d028eff92d586de0485ac37822e597d049c74f"
        const val HMAC_KEY100 = "c1b751501db12b753069a918627b623179be55c8cc7ca5c4e14bdf32274ac656"
        const val HMAC_EMPTY_KEY = "4a79a31b10a8c34b7c89735b8088e6b810b4bad4f36d671b65ca2ea0914952bf"
        const val KDF1 = "87b7f70b24183c6db5dc4f6d9f4c47cdb203321aa2a0bef714896adb3107a040"
        const val KDF2 = "538a02e1eb2e31b7e4d4b82476d1fe2acf50085d3fb584537279195f5d1f5824"
        const val KDF3 = "9ad858025593be88de0f9442a589cfe84a88dc19e4753d93684d1c5efc4837f9"
        const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        const val ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        const val BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
        const val SHARED_SECRET = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        const val HCHACHA_SUBKEY = "82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc"
        const val CHACHA_BLOCK0 = "76b8e0ada0f13d90405d6ae55386bd28bdd219b8a08ded1aa836efcc8b770dc7" +
            "da41597c5157488d7724e03fb8d84a376a43b8f41518a11cc387b669b2ee6586"
        const val TAI64N_ZERO = "400000000000000a00000000"
        const val TAI64N_SAMPLE = "400000006553f10a07000000"
        const val SUNSCREEN = "Ladies and Gentlemen of the class of '99: " +
            "If I could offer you only one tip for the future, sunscreen would be it."
        const val XCHACHA_SEALED = "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
            "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
            "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
            "21f9664c97637da9768812f615c68b13b52ec0875924c1c7987947deafd8780a" +
            "cf49"
    }
}
