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
 * Server side of the AmneziaWG framing (padding prefixes, custom message types and header
 * protection), written from the protocol description for tests. Header keys must be hex.
 */
internal class TestAwgFraming(
    private val settings: AwgObfuscationSettings = AwgObfuscationSettings(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val key: ByteArray? = settings.headerProtectionKey.trim().takeIf { it.isNotEmpty() }
        ?.let(::hexBytes)
        ?.takeUnless { bytes -> bytes.all { it == 0.toByte() } }

    private val initiationTypes = range(settings.h1, 1)
    private val transportTypes = range(settings.h4, 4)

    val responseType: Int = range(settings.h2, 2).first.toInt()
    val cookieType: Int = range(settings.h3, 3).first.toInt()
    val transportType: Int = transportTypes.first.toInt()

    /** The initiation inside [datagram], or null for decoys and any other traffic. */
    fun unwrapInitiation(datagram: ByteArray): ByteArray? {
        if (datagram.size != settings.s1 + INITIATION_SIZE) return null
        val message = unmask(datagram, settings.s1, INITIATION_SIZE)
        return message.takeIf { it.uint32Le(0) in initiationTypes }
    }

    fun unwrapTransport(datagram: ByteArray): ByteArray? {
        if (datagram.size < settings.s4 + TRANSPORT_MIN_SIZE) return null
        val message = unmask(datagram, settings.s4, TRANSPORT_HEADER_SIZE)
        return message.takeIf { it.uint32Le(0) in transportTypes }
    }

    fun wrapResponse(message: ByteArray): ByteArray = mask(message, settings.s2, message.size)

    fun wrapCookieReply(message: ByteArray): ByteArray = mask(message, settings.s3, message.size)

    fun wrapTransport(message: ByteArray): ByteArray = mask(message, settings.s4, TRANSPORT_HEADER_SIZE)

    private fun unmask(datagram: ByteArray, padding: Int, protectedBytes: Int): ByteArray {
        val message = datagram.copyOfRange(padding, datagram.size)
        key?.let { xorKeystream(it, datagram.copyOfRange(0, NONCE_SIZE), message, protectedBytes) }
        return message
    }

    private fun mask(message: ByteArray, padding: Int, protectedBytes: Int): ByteArray {
        val datagram = ByteArray(padding + message.size)
        ByteArray(padding).also(random::nextBytes).copyInto(datagram)
        val body = message.copyOf()
        key?.let { xorKeystream(it, datagram.copyOfRange(0, NONCE_SIZE), body, protectedBytes) }
        body.copyInto(datagram, padding)
        return datagram
    }

    private fun xorKeystream(key: ByteArray, nonce: ByteArray, data: ByteArray, length: Int) {
        val keystream = ByteArray(length)
        WireGuardCrypto.chaCha20Xor(key, nonce, keystream, 0, length)
        for (index in 0 until length) data[index] = (data[index].toInt() xor keystream[index].toInt()).toByte()
    }

    private companion object {
        const val INITIATION_SIZE = 148
        const val TRANSPORT_HEADER_SIZE = 16
        const val TRANSPORT_MIN_SIZE = 32
        const val NONCE_SIZE = 12

        fun range(spec: String, default: Long): LongRange {
            val bounds = spec.trim().ifEmpty { default.toString() }.split('-').map { it.trim().toLong() }
            return bounds.first()..bounds.last()
        }
    }
}
