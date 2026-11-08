/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64

/** Raised for AmneziaWG settings that the tunnel implementation would reject as well. */
internal class AwgConfigException(message: String) : Exception(message)

/** AmneziaWG parameters exactly as stored in the connection settings. */
internal data class AwgObfuscationSettings(
    val jc: Int = 0,
    val jmin: Int = 0,
    val jmax: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val s3: Int = 0,
    val s4: Int = 0,
    val h1: String = "",
    val h2: String = "",
    val h3: String = "",
    val h4: String = "",
    val i1: String = "",
    val i2: String = "",
    val i3: String = "",
    val i4: String = "",
    val i5: String = "",
    val headerProtectionKey: String = "",
    val contentPaddingAddition: String = "",
)

/** Inclusive uint32 range: an AmneziaWG H1-H4 message type or a content padding size. */
internal data class AwgTypeRange(val low: Long, val high: Long) {
    operator fun contains(value: Long): Boolean = value in low..high

    fun pick(random: SecureRandom): Int {
        val span = high - low + 1
        val offset = if (span <= 1L) 0L else Math.floorMod(random.nextLong(), span)
        return (low + offset).toInt()
    }

    companion object {
        private const val UINT32_MAX = 0xFFFFFFFFL

        fun single(value: Long) = AwgTypeRange(value, value)

        /** Parses "x" or "x-y" like amneziawg-go; a blank value keeps the WireGuard message type. */
        fun parse(spec: String, defaultType: Long): AwgTypeRange {
            val trimmed = spec.trim()
            if (trimmed.isEmpty()) return single(defaultType)
            val parts = trimmed.split('-')
            if (parts.size > 2) throw AwgConfigException("Invalid header range: $trimmed")
            val values = parts.map { part ->
                part.trim().toLongOrNull()?.takeIf { it in 0..UINT32_MAX }
                    ?: throw AwgConfigException("Invalid header range: $trimmed")
            }
            if (values.first() > values.last()) throw AwgConfigException("Invalid header range: $trimmed")
            return AwgTypeRange(values.first(), values.last())
        }
    }
}

/** One I1-I5 special packet. Tag syntax and semantics mirror amneziawg-go's obfuscation chains. */
internal class AwgSpecialPacket private constructor(private val parts: List<Part>) {

    private sealed interface Part

    private class Literal(val bytes: ByteArray) : Part

    private data object Timestamp : Part

    private class RandomBytes(val length: Int) : Part

    private class RandomLetters(val length: Int) : Part

    private class RandomDigits(val length: Int) : Part

    private class Zeros(val length: Int) : Part

    fun build(random: SecureRandom, unixSeconds: Long): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) {
            val chunk = when (part) {
                is Literal -> part.bytes
                Timestamp -> ByteArray(4).also { it.writeIntBe(0, unixSeconds.toInt()) }
                is RandomBytes -> ByteArray(part.length).also(random::nextBytes)
                is RandomLetters -> randomFrom(LETTERS, part.length, random)
                is RandomDigits -> randomFrom(DIGITS, part.length, random)
                is Zeros -> ByteArray(part.length)
            }
            out.write(chunk, 0, chunk.size)
        }
        return out.toByteArray()
    }

    private fun randomFrom(alphabet: String, length: Int, random: SecureRandom): ByteArray {
        val bytes = ByteArray(length).also(random::nextBytes)
        for (index in bytes.indices) {
            bytes[index] = alphabet[(bytes[index].toInt() and 0xFF) % alphabet.length].code.toByte()
        }
        return bytes
    }

    companion object {
        private const val LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val MAX_PART_LENGTH = 65_507
        private val WHITESPACE = Regex("\\s+")

        /** Returns null when [spec] has no tags: amneziawg-go then sends nothing for this slot. */
        fun parse(spec: String): AwgSpecialPacket? {
            val parts = ArrayList<Part>()
            val errors = ArrayList<String>()
            var remaining = spec
            while (true) {
                val start = remaining.indexOf('<')
                if (start < 0) break
                val end = remaining.indexOf('>', start)
                if (end < 0) throw AwgConfigException("missing enclosing >")
                val fields = remaining.substring(start + 1, end).split(WHITESPACE).filter { it.isNotEmpty() }
                remaining = remaining.substring(end + 1)
                if (fields.isEmpty()) {
                    errors += "empty tag"
                    continue
                }
                val value = fields.getOrElse(1) { "" }
                val part: Part? = when (fields[0]) {
                    "b" -> parseLiteral(value)
                    "t" -> Timestamp
                    "r" -> parseLength(value)?.let(::RandomBytes)
                    "rc" -> parseLength(value)?.let(::RandomLetters)
                    "rd" -> parseLength(value)?.let(::RandomDigits)
                    "d", "ds" -> Zeros(0)
                    "dz" -> parseLength(value)?.let(::Zeros)
                    else -> null
                }
                if (part == null) errors += "invalid tag <" + fields[0] + ">" else parts += part
            }
            if (errors.isNotEmpty()) throw AwgConfigException(errors.joinToString("; "))
            return if (parts.isEmpty()) null else AwgSpecialPacket(parts)
        }

        private fun parseLiteral(value: String): Part? {
            val hex = value.removePrefix("0x")
            if (hex.isEmpty()) return null
            return decodeHex(hex)?.let(::Literal)
        }

        private fun parseLength(value: String): Int? = value.toIntOrNull()?.takeIf { it in 0..MAX_PART_LENGTH }
    }
}

/**
 * The AmneziaWG obfuscation of hoaxisr/amneziawg-go, the tunnel implementation used by the app:
 * decoy packets, padded handshake messages, custom message types, header protection and
 * transport content padding.
 * Probes must be byte-for-byte what the real tunnel sends, otherwise DPI could treat them
 * differently, so every rule here follows the fork's send and receive paths.
 */
internal class AwgObfuscation private constructor(
    private val junkCount: Int,
    private val junkMin: Int,
    private val junkMax: Int,
    private val initPadding: Int,
    private val responsePadding: Int,
    private val cookiePadding: Int,
    private val transportPadding: Int,
    private val initType: AwgTypeRange,
    private val responseType: AwgTypeRange,
    private val cookieType: AwgTypeRange,
    private val transportType: AwgTypeRange,
    private val headerProtectionKey: ByteArray?,
    private val specialPackets: List<AwgSpecialPacket>,
    private val contentPadding: AwgTypeRange?,
) {
    enum class Kind { RESPONSE, COOKIE_REPLY, TRANSPORT }

    class Message(val kind: Kind, val bytes: ByteArray)

    fun initiationType(random: SecureRandom): Int = initType.pick(random)

    fun transportMessageType(random: SecureRandom): Int = transportType.pick(random)

    /**
     * Zero bytes the tunnel appends to a [packetSize]-byte transport payload before sealing it, as the
     * fork's send path does. With a content padding range, randomPaddingAddition draws a size from it
     * and caps it to the free room in the peer's UDP window; [udpWindow] must already include this
     * packet (see [udpWindowAfterSend]). Otherwise calculatePaddingSize rounds the payload up to a
     * multiple of 16 within the MTU.
     */
    fun transportContentPadding(packetSize: Int, mtu: Int, udpWindow: Int, random: SecureRandom): Int {
        require(packetSize >= 0 && mtu > 0 && udpWindow >= 0) { "Invalid transport padding input" }
        val addition = contentPadding
        if (addition == null) {
            val lastUnit = if (packetSize > mtu) packetSize % mtu else packetSize
            return minOf((lastUnit + PADDING_MULTIPLE - 1) / PADDING_MULTIPLE * PADDING_MULTIPLE, mtu) - lastUnit
        }
        val datagram = transportPadding + WireGuardHandshake.TRANSPORT_MIN_SIZE + packetSize
        if (udpWindow < datagram) return 0
        val span = addition.high - addition.low + 1
        val size = addition.low + if (span > 1) Math.floorMod(random.nextLong(), span) else 0L
        return minOf(size, (udpWindow - datagram).toLong()).toInt()
    }

    /**
     * The fork's per-peer UDP window after sending a transport message with a [packetSize]-byte
     * payload: the largest datagram so far, counted before content padding. A new peer starts at
     * [DEFAULT_UDP_WINDOW], so the first packets of a fresh tunnel have less room for padding than
     * those of a tunnel that already carried full-size packets.
     */
    fun udpWindowAfterSend(udpWindow: Int, packetSize: Int): Int =
        maxOf(udpWindow, transportPadding + WireGuardHandshake.TRANSPORT_MIN_SIZE + packetSize)

    /** The window after receiving a transport message whose decrypted payload is [payloadSize] bytes. */
    fun udpWindowAfterReceive(udpWindow: Int, payloadSize: Int): Int =
        maxOf(udpWindow, transportPadding + WireGuardHandshake.TRANSPORT_HEADER_SIZE + payloadSize)

    /** Datagrams sent before every initiation: I1..I5 first, then Jc junk packets. */
    fun decoys(random: SecureRandom, unixSeconds: Long): List<ByteArray> {
        val result = ArrayList<ByteArray>(specialPackets.size + junkCount)
        specialPackets.forEach { result += it.build(random, unixSeconds) }
        repeat(junkCount) {
            val size = junkMin + if (junkMax > junkMin) random.nextInt(junkMax - junkMin) else 0
            result += ByteArray(size).also(random::nextBytes)
        }
        return result
    }

    fun wrapInitiation(message: ByteArray, random: SecureRandom): ByteArray =
        wrap(message, initPadding, message.size, random)

    fun wrapTransport(message: ByteArray, random: SecureRandom): ByteArray =
        wrap(message, transportPadding, WireGuardHandshake.TRANSPORT_HEADER_SIZE, random)

    /** Classifies a received datagram like amneziawg-go and strips padding and header protection. */
    fun unwrap(datagram: ByteArray, length: Int): Message? {
        val keystream = headerProtectionKey?.let { key ->
            if (length < NONCE_SIZE) return null
            ByteArray(WireGuardHandshake.RESPONSE_SIZE).also {
                WireGuardCrypto.chaCha20Xor(key, datagram.copyOfRange(0, NONCE_SIZE), it, 0, it.size)
            }
        }
        return when {
            length == responsePadding + WireGuardHandshake.RESPONSE_SIZE &&
                typeAt(datagram, responsePadding, keystream) in responseType ->
                extract(datagram, responsePadding, WireGuardHandshake.RESPONSE_SIZE, WireGuardHandshake.RESPONSE_SIZE, keystream, Kind.RESPONSE)

            length == cookiePadding + WireGuardHandshake.COOKIE_REPLY_SIZE &&
                typeAt(datagram, cookiePadding, keystream) in cookieType ->
                extract(datagram, cookiePadding, WireGuardHandshake.COOKIE_REPLY_SIZE, WireGuardHandshake.COOKIE_REPLY_SIZE, keystream, Kind.COOKIE_REPLY)

            length >= transportPadding + WireGuardHandshake.TRANSPORT_MIN_SIZE &&
                typeAt(datagram, transportPadding, keystream) in transportType ->
                extract(datagram, transportPadding, length - transportPadding, WireGuardHandshake.TRANSPORT_HEADER_SIZE, keystream, Kind.TRANSPORT)

            else -> null
        }
    }

    private fun wrap(message: ByteArray, padding: Int, protectedBytes: Int, random: SecureRandom): ByteArray {
        val datagram = ByteArray(padding + message.size)
        if (padding > 0) ByteArray(padding).also(random::nextBytes).copyInto(datagram)
        message.copyInto(datagram, padding)
        headerProtectionKey?.let { key ->
            // Security: header protection hides the WireGuard header from DPI; the nonce is the
            // random padding prefix, exactly as in amneziawg-go.
            WireGuardCrypto.chaCha20Xor(key, datagram.copyOfRange(0, NONCE_SIZE), datagram, padding, protectedBytes)
        }
        return datagram
    }

    private fun typeAt(datagram: ByteArray, offset: Int, keystream: ByteArray?): Long {
        var value = 0L
        for (index in 0 until 4) {
            val mask = keystream?.get(index)?.toInt() ?: 0
            val byte = (datagram[offset + index].toInt() xor mask) and 0xFF
            value = value or (byte.toLong() shl (8 * index))
        }
        return value
    }

    private fun extract(
        datagram: ByteArray,
        offset: Int,
        size: Int,
        protectedBytes: Int,
        keystream: ByteArray?,
        kind: Kind,
    ): Message {
        val bytes = datagram.copyOfRange(offset, offset + size)
        if (keystream != null) {
            for (index in 0 until protectedBytes) {
                bytes[index] = (bytes[index].toInt() xor keystream[index].toInt()).toByte()
            }
        }
        return Message(kind, bytes)
    }

    companion object {
        private const val NONCE_SIZE = WireGuardCrypto.CHACHA_NONCE_SIZE
        private const val MAX_DATAGRAM_SIZE = 65_507
        private const val MAX_PADDING = 65_000
        private const val PADDING_MULTIPLE = 16

        /** DefaultUdpWindow in amneziawg-go: the UDP window of a peer that has not carried transport data yet. */
        const val DEFAULT_UDP_WINDOW = 500

        /** Plain WireGuard: no decoys, no padding, standard message types. */
        val STANDARD = AwgObfuscation(
            junkCount = 0,
            junkMin = 0,
            junkMax = 0,
            initPadding = 0,
            responsePadding = 0,
            cookiePadding = 0,
            transportPadding = 0,
            initType = AwgTypeRange.single(WireGuardHandshake.MESSAGE_INITIATION),
            responseType = AwgTypeRange.single(WireGuardHandshake.MESSAGE_RESPONSE),
            cookieType = AwgTypeRange.single(WireGuardHandshake.MESSAGE_COOKIE_REPLY),
            transportType = AwgTypeRange.single(WireGuardHandshake.MESSAGE_TRANSPORT),
            headerProtectionKey = null,
            specialPackets = emptyList(),
            contentPadding = null,
        )

        fun fromSettings(settings: AwgObfuscationSettings): AwgObfuscation = with(settings) {
            if (listOf(jc, jmin, jmax, s1, s2, s3, s4).any { it < 0 }) {
                throw AwgConfigException("Negative AmneziaWG parameter")
            }
            if (jmin > MAX_DATAGRAM_SIZE || jmax > MAX_DATAGRAM_SIZE) {
                throw AwgConfigException("Junk packets are larger than a UDP datagram")
            }
            if (listOf(s1, s2, s3, s4).any { it > MAX_PADDING }) throw AwgConfigException("Padding is too large")
            val protectionKey = parseHeaderProtectionKey(headerProtectionKey)
            if (protectionKey != null && listOf(s1, s2, s3, s4).any { it < NONCE_SIZE }) {
                throw AwgConfigException("Header protection requires S1-S4 of at least $NONCE_SIZE")
            }
            AwgObfuscation(
                junkCount = jc,
                junkMin = jmin,
                junkMax = jmax,
                initPadding = s1,
                responsePadding = s2,
                cookiePadding = s3,
                transportPadding = s4,
                initType = AwgTypeRange.parse(h1, WireGuardHandshake.MESSAGE_INITIATION),
                responseType = AwgTypeRange.parse(h2, WireGuardHandshake.MESSAGE_RESPONSE),
                cookieType = AwgTypeRange.parse(h3, WireGuardHandshake.MESSAGE_COOKIE_REPLY),
                transportType = AwgTypeRange.parse(h4, WireGuardHandshake.MESSAGE_TRANSPORT),
                headerProtectionKey = protectionKey,
                specialPackets = listOf(i1, i2, i3, i4, i5).filter { it.isNotBlank() }.mapNotNull { AwgSpecialPacket.parse(it) },
                contentPadding = parseContentPadding(contentPaddingAddition),
            )
        }

        /** 64 hex characters or base64 of 32 bytes; an all-zero key means "disabled", as in amneziawg-go. */
        private fun parseHeaderProtectionKey(value: String): ByteArray? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val key = decodeHex(trimmed) ?: try {
                Base64.getDecoder().decode(trimmed)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (key == null || key.size != WireGuardCrypto.KEY_SIZE) {
                throw AwgConfigException("Invalid header protection key")
            }
            return key.takeUnless { bytes -> bytes.all { it == 0.toByte() } }
        }

        /** "x" or "x-y" extra bytes, like amneziawg-go's UintRange; blank or "0" keeps the standard padding. */
        private fun parseContentPadding(value: String): AwgTypeRange? {
            if (value.isBlank()) return null
            val range = try {
                AwgTypeRange.parse(value, 0L)
            } catch (_: AwgConfigException) {
                throw AwgConfigException("Invalid content padding addition")
            }
            return range.takeUnless { it.low == 0L && it.high == 0L }
        }
    }
}
