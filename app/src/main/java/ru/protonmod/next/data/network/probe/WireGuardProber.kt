/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Strictly increasing TAI64N timestamps per server key. WireGuard ignores an initiation whose
 * timestamp is not newer than the last one it accepted for the same key, so back-to-back probes
 * never reuse a timestamp, and they never run ahead of real time by more than a few ms.
 */
internal class HandshakeTimestampSource(
    private val unixNanos: () -> Long = { System.currentTimeMillis() * 1_000_000L },
) {
    private val lastByServer = ConcurrentHashMap<String, Long>()

    fun next(serverKey: String): ByteArray {
        val value = lastByServer.compute(serverKey) { _, previous ->
            val now = unixNanos()
            if (previous == null) now else maxOf(now, previous + WireGuardCrypto.TAI64N_GRANULARITY_NANOS)
        } ?: unixNanos()
        return WireGuardCrypto.tai64n(value)
    }
}

/**
 * Checks one WireGuard server without creating a tunnel: a real Noise_IKpsk2 handshake over a
 * plain UDP socket and, optionally, one DNS query through the resulting session.
 *
 * Security-sensitive: holds a copy of the account's WireGuard private key for the duration of a
 * scan. Only the server's own key can produce a reply that decrypts, so "available" cannot be
 * spoofed by a censor.
 */
internal class WireGuardProber(
    staticPrivateKey: ByteArray,
    private val random: SecureRandom,
    private val timestamps: HandshakeTimestampSource,
    private val socketFactory: () -> DatagramSocket,
) {
    private val staticPrivate = staticPrivateKey.copyOf()

    sealed interface Outcome {
        data class Verified(
            val ports: List<Int>,
            val bestPort: Int,
            val latencyMs: Int,
            val dataPlane: DataPlane,
        ) : Outcome

        data object CookieOnly : Outcome

        data object InvalidResponse : Outcome

        data object NoResponse : Outcome

        data object InvalidKey : Outcome

        data class Failed(val reason: String) : Outcome
    }

    enum class DataPlane { NOT_CHECKED, VERIFIED, BLOCKED, INCONCLUSIVE }

    class DataCheck(val tunnelAddress: Inet4Address, val dnsAddress: Inet4Address)

    class Config(
        val ports: List<Int>,
        val timeoutMs: Int,
        val attempts: Int,
        val obfuscation: AwgObfuscation,
        /** Tunnel MTU; transport padding never crosses it, as in the fork. */
        val mtu: Int,
        val dataCheck: DataCheck? = null,
    )

    suspend fun probe(serverPublicKey: ByteArray, address: InetAddress, config: Config): Outcome {
        require(config.ports.isNotEmpty() && config.attempts > 0 && config.timeoutMs > 0 && config.mtu > 0) {
            "Invalid probe configuration"
        }
        val keys = WireGuardPeerKeys(staticPrivate, serverPublicKey)
        if (keys.staticStatic == null) return Outcome.InvalidKey
        val socket = try {
            socketFactory()
        } catch (error: IOException) {
            return Outcome.Failed(error.javaClass.simpleName)
        }
        return try {
            ProbeRun(socket, keys, Base64.getEncoder().encodeToString(serverPublicKey), address, config).execute()
        } catch (error: IOException) {
            Outcome.Failed(error.javaClass.simpleName)
        } finally {
            socket.close()
        }
    }

    private class Initiation(
        val port: Int,
        val state: InitiatorState,
        val sentAt: Long,
        val cookieRetries: Int,
        val sequence: Int,
    )

    private class Session(val port: Int, val latencyMs: Int, val sequence: Int, val keys: TransportKeys)

    private inner class ProbeRun(
        private val socket: DatagramSocket,
        private val keys: WireGuardPeerKeys,
        private val serverKey: String,
        private val address: InetAddress,
        private val config: Config,
    ) {
        private val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
        private val timeoutNanos = config.timeoutMs * NANOS_PER_MILLI
        private val pending = HashMap<Int, Initiation>()
        private val sessions = LinkedHashMap<Int, Session>()
        private val cookies = HashMap<Int, ByteArray>()
        private var sequence = 0
        private var sawCookie = false
        private var sawInvalid = false
        private var lastFromTarget = false

        suspend fun execute(): Outcome {
            var attempt = 0
            while (attempt < config.attempts && sessions.isEmpty()) {
                config.ports.forEachIndexed { index, port ->
                    // wireguard-go drops initiations that arrive less than 20 ms apart.
                    if (index > 0) delay(INITIATION_SPACING_MS)
                    sendInitiation(port, cookies[port], cookieRetries = 0)
                }
                awaitHandshakes()
                attempt++
            }
            if (sessions.isEmpty()) {
                return when {
                    sawCookie -> Outcome.CookieOnly
                    sawInvalid -> Outcome.InvalidResponse
                    else -> Outcome.NoResponse
                }
            }
            val best = sessions.values.minBy { it.latencyMs }
            val dataPlane = config.dataCheck?.let { checkDataPlane(it, best.port) } ?: DataPlane.NOT_CHECKED
            return Outcome.Verified(sessions.keys.sorted(), best.port, best.latencyMs, dataPlane)
        }

        private suspend fun awaitHandshakes() {
            var deadline = System.nanoTime() + timeoutNanos
            var graceStarted = false
            while (true) {
                val length = receive(deadline) ?: return
                val receivedAt = System.nanoTime()
                val message = config.obfuscation.unwrap(buffer, length)
                if (message == null) {
                    if (lastFromTarget) sawInvalid = true
                    continue
                }
                when (message.kind) {
                    AwgObfuscation.Kind.RESPONSE -> {
                        val initiation = pending[message.bytes.readIntLe(8)]
                        val transport = initiation?.let { WireGuardHandshake.consumeResponse(keys, it.state, message.bytes) }
                        if (initiation == null || transport == null) {
                            if (lastFromTarget) sawInvalid = true
                            continue
                        }
                        pending.remove(initiation.state.senderIndex)
                        if (initiation.port !in sessions) {
                            val latencyMs = ((receivedAt - initiation.sentAt) / NANOS_PER_MILLI).toInt()
                            sessions[initiation.port] = Session(initiation.port, latencyMs, initiation.sequence, transport)
                        }
                        if (sessions.size == config.ports.size) return
                        if (!graceStarted) {
                            // One verified port settles the verdict; the other ports get a short grace period.
                            graceStarted = true
                            deadline = minOf(deadline, receivedAt + PORT_GRACE_NANOS)
                        }
                    }

                    AwgObfuscation.Kind.COOKIE_REPLY -> {
                        val initiation = pending[message.bytes.readIntLe(4)]
                        val cookie = initiation?.let { WireGuardHandshake.consumeCookieReply(keys, it.state, message.bytes) }
                        if (initiation == null || cookie == null) {
                            if (lastFromTarget) sawInvalid = true
                            continue
                        }
                        sawCookie = true
                        pending.remove(initiation.state.senderIndex)
                        cookies[initiation.port] = cookie
                        if (initiation.port !in sessions && initiation.cookieRetries < MAX_COOKIE_RETRIES) {
                            delay(INITIATION_SPACING_MS)
                            sendInitiation(initiation.port, cookie, initiation.cookieRetries + 1)
                            deadline = maxOf(deadline, System.nanoTime() + timeoutNanos)
                        }
                    }

                    AwgObfuscation.Kind.TRANSPORT -> Unit
                }
            }
        }

        private suspend fun checkDataPlane(check: DataCheck, bestPort: Int): DataPlane {
            // The server keeps only the keys of the newest initiation it accepted, so the newest
            // session is tried first, then a fresh handshake on the fastest port.
            val latest = sessions.values.maxBy { it.sequence }
            if (exchange(check, latest.keys, latest.port)) return DataPlane.VERIFIED
            delay(INITIATION_SPACING_MS)
            val fresh = freshHandshake(bestPort) ?: return DataPlane.INCONCLUSIVE
            return if (exchange(check, fresh, bestPort)) DataPlane.VERIFIED else DataPlane.BLOCKED
        }

        /** Sends DNS queries through [session]; any authenticated, non-empty reply proves data flow. */
        private suspend fun exchange(check: DataCheck, session: TransportKeys, port: Int): Boolean {
            var counter = 0L
            // Like a newly connected tunnel, the session starts with the fork's default UDP window.
            var window = AwgObfuscation.DEFAULT_UDP_WINDOW
            repeat(config.attempts) {
                val packet = DnsProbePacket.buildIpv4Query(
                    check.tunnelAddress,
                    check.dnsAddress,
                    EPHEMERAL_PORT_BASE + random.nextInt(EPHEMERAL_PORT_COUNT),
                    random.nextInt(DNS_ID_RANGE),
                )
                window = config.obfuscation.udpWindowAfterSend(window, packet.size)
                send(transportDatagram(session, counter++, packet, window), port)
                val deadline = System.nanoTime() + timeoutNanos
                while (true) {
                    val length = receive(deadline) ?: break
                    val reply = config.obfuscation.unwrap(buffer, length) ?: continue
                    if (reply.kind != AwgObfuscation.Kind.TRANSPORT) continue
                    val payload = WireGuardHandshake.openTransport(session, reply.bytes) ?: continue
                    window = config.obfuscation.udpWindowAfterReceive(window, payload.size)
                    if (payload.isNotEmpty()) {
                        sendKeepalive(session, counter, port, window)
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Acknowledges the reply the way wireguard-go's passive keepalive would. A server that sent
         * data and then hears nothing retries handshakes towards the closed probe socket for up to
         * 90 seconds, so one keepalive keeps every scanned server quiet afterwards.
         */
        private fun sendKeepalive(session: TransportKeys, counter: Long, port: Int, window: Int) {
            try {
                val keepalive = ByteArray(0)
                val grown = config.obfuscation.udpWindowAfterSend(window, keepalive.size)
                send(transportDatagram(session, counter, keepalive, grown), port)
            } catch (_: IOException) {
                // Best effort: the data plane has already been verified.
            }
        }

        /**
         * [packet] framed exactly as the tunnel sends it; an empty packet is a keepalive. [window] is
         * the session's UDP window including this packet, which bounds content padding.
         */
        private fun transportDatagram(session: TransportKeys, counter: Long, packet: ByteArray, window: Int): ByteArray {
            val message = WireGuardHandshake.sealTransport(
                session,
                counter,
                config.obfuscation.transportMessageType(random),
                packet,
                config.obfuscation.transportContentPadding(packet.size, config.mtu, window, random),
            )
            return config.obfuscation.wrapTransport(message, random)
        }

        private suspend fun freshHandshake(port: Int): TransportKeys? {
            var cookie = cookies[port]
            for (round in 0..MAX_COOKIE_RETRIES) {
                if (round > 0) delay(INITIATION_SPACING_MS)
                val initiation = sendInitiation(port, cookie, round) ?: return null
                val index = initiation.state.senderIndex
                val deadline = System.nanoTime() + timeoutNanos
                var retry = false
                while (!retry) {
                    val length = receive(deadline) ?: break
                    val message = config.obfuscation.unwrap(buffer, length) ?: continue
                    when (message.kind) {
                        AwgObfuscation.Kind.RESPONSE -> if (message.bytes.readIntLe(8) == index) {
                            WireGuardHandshake.consumeResponse(keys, initiation.state, message.bytes)?.let { return it }
                        }

                        AwgObfuscation.Kind.COOKIE_REPLY -> if (message.bytes.readIntLe(4) == index) {
                            WireGuardHandshake.consumeCookieReply(keys, initiation.state, message.bytes)?.let {
                                cookie = it
                                retry = true
                            }
                        }

                        AwgObfuscation.Kind.TRANSPORT -> Unit
                    }
                }
                pending.remove(index)
                if (!retry) return null
            }
            return null
        }

        private fun sendInitiation(port: Int, cookie: ByteArray?, cookieRetries: Int): Initiation? {
            var senderIndex = random.nextInt()
            while (pending.containsKey(senderIndex)) senderIndex = random.nextInt()
            val (message, state) = WireGuardHandshake.createInitiation(
                keys,
                senderIndex,
                timestamps.next(serverKey),
                config.obfuscation.initiationType(random),
                cookie,
                random,
            ) ?: return null
            config.obfuscation.decoys(random, System.currentTimeMillis() / MILLIS_PER_SECOND).forEach { send(it, port) }
            val datagram = config.obfuscation.wrapInitiation(message, random)
            val sentAt = System.nanoTime()
            send(datagram, port)
            return Initiation(port, state, sentAt, cookieRetries, sequence++).also { pending[senderIndex] = it }
        }

        private fun send(payload: ByteArray, port: Int) {
            socket.send(DatagramPacket(payload, payload.size, address, port))
        }

        /**
         * Next datagram received before [deadline] (System.nanoTime), or null on timeout. Replies
         * from other source addresses are still processed: they are authenticated cryptographically.
         */
        private suspend fun receive(deadline: Long): Int? {
            while (true) {
                currentCoroutineContext().ensureActive()
                val remainingMs = (deadline - System.nanoTime()) / NANOS_PER_MILLI
                if (remainingMs <= 0L) return null
                socket.soTimeout = remainingMs.coerceAtMost(RECEIVE_SLICE_MS).toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                lastFromTarget = packet.address == address
                return packet.length
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val RECEIVE_BUFFER_SIZE = 8_192
        const val RECEIVE_SLICE_MS = 100L
        const val INITIATION_SPACING_MS = 40L
        const val PORT_GRACE_NANOS = 300L * NANOS_PER_MILLI
        const val MAX_COOKIE_RETRIES = 1
        const val DNS_ID_RANGE = 0x10000
        const val EPHEMERAL_PORT_BASE = 40_000
        const val EPHEMERAL_PORT_COUNT = 20_000
    }
}
