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
import java.net.Inet4Address

/** Builds the tiny DNS query used to prove that data really flows through a server. */
internal object DnsProbePacket {
    const val DNS_PORT = 53

    private const val QUERY_NAME = "example.com"
    private const val IPV4_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8
    private const val DNS_HEADER_SIZE = 12
    private const val PROTOCOL_UDP = 17
    private const val TTL = 64
    private const val TYPE_A = 1
    private const val CLASS_IN = 1
    private const val FLAG_RECURSION_DESIRED = 0x0100
    private const val FLAG_DONT_FRAGMENT = 0x4000

    /** Plain DNS message with one A question and recursion desired. */
    fun buildQuery(queryId: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteArray(DNS_HEADER_SIZE)
        header.writeShortBe(0, queryId)
        header.writeShortBe(2, FLAG_RECURSION_DESIRED)
        header.writeShortBe(4, 1)
        out.write(header, 0, header.size)
        QUERY_NAME.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes, 0, bytes.size)
        }
        out.write(0)
        val question = ByteArray(4)
        question.writeShortBe(0, TYPE_A)
        question.writeShortBe(2, CLASS_IN)
        out.write(question, 0, question.size)
        return out.toByteArray()
    }

    /** IPv4/UDP datagram carrying [buildQuery], as the tunnel would send it to the in-tunnel resolver. */
    fun buildIpv4Query(source: Inet4Address, destination: Inet4Address, sourcePort: Int, queryId: Int): ByteArray {
        val dns = buildQuery(queryId)
        val udpLength = UDP_HEADER_SIZE + dns.size
        val packet = ByteArray(IPV4_HEADER_SIZE + udpLength)
        packet[0] = 0x45
        packet.writeShortBe(2, packet.size)
        packet.writeShortBe(4, queryId)
        packet.writeShortBe(6, FLAG_DONT_FRAGMENT)
        packet[8] = TTL.toByte()
        packet[9] = PROTOCOL_UDP.toByte()
        source.address.copyInto(packet, 12)
        destination.address.copyInto(packet, 16)
        packet.writeShortBe(10, checksum(packet, 0, IPV4_HEADER_SIZE, 0L))
        packet.writeShortBe(IPV4_HEADER_SIZE, sourcePort)
        packet.writeShortBe(IPV4_HEADER_SIZE + 2, DNS_PORT)
        packet.writeShortBe(IPV4_HEADER_SIZE + 4, udpLength)
        dns.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)
        val pseudoHeader = sumWords(packet, 12, 8) + PROTOCOL_UDP + udpLength
        val udpChecksum = checksum(packet, IPV4_HEADER_SIZE, udpLength, pseudoHeader)
        packet.writeShortBe(IPV4_HEADER_SIZE + 6, if (udpChecksum == 0) 0xFFFF else udpChecksum)
        return packet
    }

    /** True when the first [length] bytes of [data] are a DNS response to [queryId]. */
    fun isResponse(data: ByteArray, length: Int, queryId: Int): Boolean =
        length >= DNS_HEADER_SIZE &&
            data.readShortBe(0) == (queryId and 0xFFFF) &&
            (data[2].toInt() and 0x80) != 0

    private fun sumWords(data: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = 0
        while (index + 1 < length) {
            sum += data.readShortBe(offset + index)
            index += 2
        }
        if (index < length) sum += (data[offset + index].toInt() and 0xFF) shl 8
        return sum
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int, initial: Long): Int {
        var sum = initial + sumWords(data, offset, length)
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
