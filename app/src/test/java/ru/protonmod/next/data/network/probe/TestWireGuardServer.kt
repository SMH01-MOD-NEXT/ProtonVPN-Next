/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Collections
import kotlin.concurrent.thread

/**
 * UDP sockets on the loopback interface. Every datagram goes to [handler], one call at a time
 * across all sockets, and the returned datagrams are sent back to the sender after [replyDelayMs].
 */
internal class LoopbackUdpServer(
    count: Int,
    private val replyDelayMs: (socketIndex: Int) -> Long = { 0L },
    private val handler: (socketIndex: Int, datagram: ByteArray) -> List<ByteArray>,
) : AutoCloseable {

    private val lock = Any()
    private val sockets = List(count) { DatagramSocket(0, InetAddress.getLoopbackAddress()) }

    val address: InetAddress = InetAddress.getLoopbackAddress()
    val ports: List<Int> = sockets.map { it.localPort }

    private val workers = sockets.mapIndexed { index, socket ->
        thread(isDaemon = true, name = "loopback-udp-" + index) { serve(index, socket) }
    }

    private fun serve(index: Int, socket: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: IOException) {
                return
            }
            val replies = synchronized(lock) { handler(index, buffer.copyOf(packet.length)) }
            if (replies.isEmpty()) continue
            val delayMs = replyDelayMs(index)
            if (delayMs > 0) Thread.sleep(delayMs)
            try {
                for (reply in replies) socket.send(DatagramPacket(reply, reply.size, packet.socketAddress))
            } catch (e: IOException) {
                return
            }
        }
    }

    override fun close() {
        sockets.forEach { it.close() }
        workers.forEach { it.join(JOIN_TIMEOUT_MS) }
    }

    private companion object {
        const val MAX_DATAGRAM = 65_535
        const val JOIN_TIMEOUT_MS = 2_000L
    }
}

/**
 * In-process WireGuard/AmneziaWG server for prober tests. Where it matters it behaves like
 * wireguard-go: replayed timestamps are ignored, cookie replies leave the initiation unconsumed,
 * and only the newest handshake's keys are kept until the initiator uses them. Transport messages
 * carrying a DNS query are answered with a DNS response; keepalives are only counted.
 */
internal class TestWireGuardServer(
    settings: AwgObfuscationSettings = AwgObfuscationSettings(),
    portCount: Int = 1,
    private val cookies: CookieMode = CookieMode.NEVER,
    /** Handshakes answered before the server stops responding to initiations. */
    private val handshakeLimit: Int = Int.MAX_VALUE,
    private val answerData: Boolean = true,
    replyDelayMs: (portIndex: Int) -> Long = { 0L },
) : AutoCloseable {

    enum class CookieMode { NEVER, UNTIL_MAC2, ALWAYS }

    class DnsQuery(val source: InetAddress, val destination: InetAddress, val id: Int)

    private val random = SecureRandom()
    private val responder = TestWireGuardResponder(WireGuardCrypto.generatePrivateKey(random), random)
    private val framing = TestAwgFraming(settings, random)
    private val cookie = ByteArray(16).also(random::nextBytes)
    private var lastTimestamp: ByteArray? = null
    private var nextIndex = 1
    private var current: TransportKeys? = null
    private var next: TransportKeys? = null
    private val sendCounters = HashMap<Int, Long>()

    @Volatile
    var handshakes = 0
        private set

    @Volatile
    var replays = 0
        private set

    @Volatile
    var cookieReplies = 0
        private set

    @Volatile
    var validMac2 = 0
        private set

    @Volatile
    var decoys = 0
        private set

    @Volatile
    var keepalives = 0
        private set

    val initiationTypes: MutableList<Long> = Collections.synchronizedList(ArrayList())
    val dnsQueries: MutableList<DnsQuery> = Collections.synchronizedList(ArrayList())

    private val udp = LoopbackUdpServer(portCount, replyDelayMs) { _, datagram -> handle(datagram) }

    val publicKey: ByteArray get() = responder.staticPublic
    val address: InetAddress get() = udp.address
    val ports: List<Int> get() = udp.ports

    private fun handle(datagram: ByteArray): List<ByteArray> {
        framing.unwrapInitiation(datagram)?.let { message ->
            responder.consumeInitiation(message)?.let { return onInitiation(it) }
        }
        framing.unwrapTransport(datagram)?.let { message ->
            onTransport(message)?.let { return it }
        }
        decoys++
        return emptyList()
    }

    private fun onInitiation(initiation: TestWireGuardResponder.Initiation): List<ByteArray> {
        initiationTypes += initiation.messageType.toLong() and 0xFFFFFFFFL
        val hasMac2 = responder.hasValidMac2(initiation, cookie)
        if (hasMac2) validMac2++
        if (cookies == CookieMode.ALWAYS || (cookies == CookieMode.UNTIL_MAC2 && !hasMac2)) {
            cookieReplies++
            return listOf(framing.wrapCookieReply(responder.createCookieReply(initiation, cookie, framing.cookieType)))
        }
        val last = lastTimestamp
        if (last != null && compareUnsigned(initiation.timestamp, last) <= 0) {
            replays++
            return emptyList()
        }
        lastTimestamp = initiation.timestamp
        if (handshakes >= handshakeLimit) return emptyList()
        handshakes++
        val response = responder.createResponse(initiation, nextIndex++, framing.responseType)
        // Like a wireguard-go responder: the new keys wait as "next", replacing older unused ones.
        next = response.keys
        return listOf(framing.wrapResponse(response.message))
    }

    private fun onTransport(message: ByteArray): List<ByteArray>? {
        val receiver = message.readIntLe(4)
        val keys = listOfNotNull(next, current).firstOrNull { it.localIndex == receiver } ?: return null
        val packet = WireGuardHandshake.openTransport(keys, message) ?: return null
        if (keys === next) {
            current = keys
            next = null
        }
        // Keepalives carry no payload, or only the zero content padding the tunnel may add.
        if (packet.all { it == 0.toByte() }) {
            keepalives++
            return emptyList()
        }
        val (query, reply) = parseDnsQuery(packet) ?: return emptyList()
        dnsQueries += query
        if (!answerData) return emptyList()
        val counter = sendCounters.getOrDefault(keys.localIndex, 0L)
        sendCounters[keys.localIndex] = counter + 1
        return listOf(framing.wrapTransport(WireGuardHandshake.sealTransport(keys, counter, framing.transportType, reply, 0)))
    }

    /** The query and a matching response packet, or null if [packet] is not an IPv4 DNS query. */
    private fun parseDnsQuery(packet: ByteArray): Pair<DnsQuery, ByteArray>? {
        if (packet.size < IPV4_HEADER + UDP_HEADER + DNS_HEADER) return null
        if (packet[0] != IPV4_NO_OPTIONS || packet[9] != PROTOCOL_UDP) return null
        val length = packet.readShortBe(2)
        if (length > packet.size || packet.readShortBe(22) != DnsProbePacket.DNS_PORT) return null
        val query = DnsQuery(
            source = InetAddress.getByAddress(packet.copyOfRange(12, 16)),
            destination = InetAddress.getByAddress(packet.copyOfRange(16, 20)),
            id = packet.readShortBe(28),
        )
        // Swap addresses and ports, drop the UDP checksum and set the DNS response flag.
        val reply = packet.copyOf(length)
        packet.copyInto(reply, 12, 16, 20)
        packet.copyInto(reply, 16, 12, 16)
        packet.copyInto(reply, 20, 22, 24)
        packet.copyInto(reply, 22, 20, 22)
        reply.writeShortBe(26, 0)
        reply[30] = (reply[30].toInt() or 0x80).toByte()
        return query to reply
    }

    override fun close() = udp.close()

    private companion object {
        const val IPV4_HEADER = 20
        const val UDP_HEADER = 8
        const val DNS_HEADER = 12
        const val IPV4_NO_OPTIONS: Byte = 0x45
        const val PROTOCOL_UDP: Byte = 17
    }
}
