/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import android.net.Network
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.security.SecureRandom

internal enum class TcpReachability { REACHABLE, UNREACHABLE, UNKNOWN }

/**
 * Scanner traffic pinned to one physical network.
 *
 * Security: every socket is bound to the underlying (non-VPN) network, so probes never travel
 * inside the user's tunnel and the results describe the path the censor actually sees.
 */
internal class AndroidProbeNetwork(
    private val network: Network,
    private val random: SecureRandom,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun openDatagramSocket(): DatagramSocket {
        val socket = DatagramSocket()
        try {
            network.bindSocket(socket)
        } catch (error: IOException) {
            socket.close()
            throw error
        }
        return socket
    }

    /** Resolves like the tunnel does: through the physical network's resolver (Private DNS included). */
    fun resolveIpv4(host: String): Inet4Address? = try {
        network.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** A TCP reset still proves the address is reachable; only silence or routing errors do not. */
    fun tcpReachability(address: InetAddress, port: Int, timeoutMs: Int): TcpReachability = try {
        network.socketFactory.createSocket().use { socket ->
            socket.connect(InetSocketAddress(address, port), timeoutMs)
        }
        TcpReachability.REACHABLE
    } catch (error: ConnectException) {
        val message = error.message.orEmpty()
        when {
            message.contains("ECONNREFUSED") || message.contains("refused", ignoreCase = true) -> TcpReachability.REACHABLE
            message.contains("ENETUNREACH") -> TcpReachability.UNKNOWN
            else -> TcpReachability.UNREACHABLE
        }
    } catch (_: SocketTimeoutException) {
        TcpReachability.UNREACHABLE
    } catch (_: NoRouteToHostException) {
        TcpReachability.UNREACHABLE
    } catch (_: IOException) {
        TcpReachability.UNKNOWN
    }

    /** True when at least one well-known TCP or UDP endpoint answers over the physical network. */
    suspend fun isInternetReachable(timeoutMs: Int): Boolean = coroutineScope {
        val tcp = BASELINE_TCP_TARGETS.map { target ->
            async(ioDispatcher) { tcpReachability(target, HTTPS_PORT, timeoutMs) == TcpReachability.REACHABLE }
        }
        val dns = BASELINE_DNS_TARGETS.map { target ->
            async(ioDispatcher) { dnsAnswers(target, timeoutMs) }
        }
        (tcp + dns).awaitAll().any { it }
    }

    private fun dnsAnswers(server: InetAddress, timeoutMs: Int): Boolean = try {
        openDatagramSocket().use { socket ->
            val queryId = random.nextInt(DNS_ID_RANGE)
            val query = DnsProbePacket.buildQuery(queryId)
            socket.soTimeout = timeoutMs
            socket.send(DatagramPacket(query, query.size, server, DnsProbePacket.DNS_PORT))
            val buffer = ByteArray(DNS_BUFFER_SIZE)
            val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MILLI
            var answered = false
            while (!answered && System.nanoTime() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                answered = packet.address == server && DnsProbePacket.isResponse(buffer, packet.length, queryId)
            }
            answered
        }
    } catch (_: IOException) {
        false
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val DNS_ID_RANGE = 0x10000
        const val DNS_BUFFER_SIZE = 512
        const val NANOS_PER_MILLI = 1_000_000L

        val BASELINE_TCP_TARGETS: List<InetAddress> = listOf(ipv4Address(1, 1, 1, 1), ipv4Address(8, 8, 8, 8))
        val BASELINE_DNS_TARGETS: List<InetAddress> = listOf(ipv4Address(77, 88, 8, 8), ipv4Address(1, 1, 1, 1))
    }
}
