/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import java.net.Inet4Address
import java.net.InetAddress

internal data class ScanTarget(val logical: LogicalServer, val physical: PhysicalServer)

/** Stored verdict for one probe outcome. */
internal data class ProbeVerdict(
    val availability: ServerAvailability,
    val detail: ServerProbeDetail,
    /** Blocked verdicts only count once a later internet check shows the phone itself was online. */
    val needsBaseline: Boolean,
)

internal object ServerScanEngine {
    private const val ONLINE = 1

    /** Runs [work] for every item on at most [parallelism] concurrent workers. */
    suspend fun <T> runAll(
        items: List<T>,
        parallelism: Int,
        dispatcher: CoroutineDispatcher,
        work: suspend (T) -> Unit,
    ) {
        if (items.isEmpty()) return
        val queue = Channel<T>(Channel.UNLIMITED)
        items.forEach { queue.trySend(it) }
        queue.close()
        coroutineScope {
            repeat(parallelism.coerceIn(1, items.size)) {
                launch(dispatcher) {
                    for (item in queue) work(item)
                }
            }
        }
    }

    /**
     * Maps a probe outcome to the stored verdict. [tcpReachability] is only consulted for servers
     * that stayed silent and returns null when the TCP check is off.
     */
    fun classify(outcome: WireGuardProber.Outcome, tcpReachability: () -> TcpReachability?): ProbeVerdict =
        when (outcome) {
            is WireGuardProber.Outcome.Verified -> when (outcome.dataPlane) {
                WireGuardProber.DataPlane.NOT_CHECKED ->
                    ProbeVerdict(ServerAvailability.AVAILABLE, ServerProbeDetail.HANDSHAKE_VERIFIED, needsBaseline = false)

                WireGuardProber.DataPlane.VERIFIED ->
                    ProbeVerdict(ServerAvailability.AVAILABLE, ServerProbeDetail.DATA_VERIFIED, needsBaseline = false)

                WireGuardProber.DataPlane.INCONCLUSIVE ->
                    ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.DATA_UNVERIFIED, needsBaseline = false)

                WireGuardProber.DataPlane.BLOCKED ->
                    ProbeVerdict(ServerAvailability.BLOCKED, ServerProbeDetail.DATA_PLANE_BLOCKED, needsBaseline = true)
            }

            WireGuardProber.Outcome.NoResponse -> {
                val detail = when (tcpReachability()) {
                    TcpReachability.REACHABLE -> ServerProbeDetail.PROTOCOL_BLOCKED
                    TcpReachability.UNREACHABLE -> ServerProbeDetail.IP_UNREACHABLE
                    TcpReachability.UNKNOWN, null -> ServerProbeDetail.NO_RESPONSE
                }
                ProbeVerdict(ServerAvailability.BLOCKED, detail, needsBaseline = true)
            }

            WireGuardProber.Outcome.CookieOnly ->
                ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.SERVER_BUSY, needsBaseline = false)

            WireGuardProber.Outcome.InvalidResponse ->
                ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.INVALID_RESPONSE, needsBaseline = false)

            WireGuardProber.Outcome.InvalidKey ->
                ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.NO_PUBLIC_KEY, needsBaseline = false)

            is WireGuardProber.Outcome.Failed ->
                ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.NETWORK_ERROR, needsBaseline = false)
        }

    /** True for servers the account may connect to that have at least one online node. */
    fun isScannable(server: LogicalServer, userTier: Int): Boolean =
        server.tier <= userTier && server.servers.any { it.status == ONLINE }

    /** Online physical servers of [scope] that the account may use, without duplicates. */
    fun selectTargets(servers: List<LogicalServer>, scope: ServerScanScope, userTier: Int): List<ScanTarget> {
        val logicals = when (scope) {
            ServerScanScope.All -> servers
            is ServerScanScope.Country -> servers.filter { it.exitCountry.equals(scope.code, ignoreCase = true) }
            is ServerScanScope.Server -> servers.filter { it.id == scope.logicalServerId }
        }
        val seen = HashSet<String>()
        return logicals.filter { it.tier <= userTier }.flatMap { logical ->
            logical.servers.filter { it.status == ONLINE && seen.add(it.id) }.map { ScanTarget(logical, it) }
        }
    }
}

/**
 * Holds "blocked" verdicts until an internet check that started after the probe had finished
 * succeeds, with no failed check since the probe started. A short outage of the phone's own
 * connection therefore never turns into "blocked by the censor".
 */
internal class BaselineGate<T> {
    data class Release<T>(val confirmed: List<T>, val unconfirmed: List<T>)

    private class Held<T>(val startedAt: Long, val finishedAt: Long, val value: T)

    private val held = ArrayList<Held<T>>()
    private var lastFailureAt = Long.MIN_VALUE

    @Synchronized
    fun hold(startedAt: Long, finishedAt: Long, value: T) {
        held += Held(startedAt, finishedAt, value)
    }

    /** Applies an internet check that ran from [checkStartedAt] to [checkFinishedAt] (monotonic clock). */
    @Synchronized
    fun onBaseline(healthy: Boolean, checkStartedAt: Long, checkFinishedAt: Long): Release<T> {
        if (!healthy) {
            lastFailureAt = maxOf(lastFailureAt, checkFinishedAt)
            return Release(emptyList(), drainLocked())
        }
        val confirmed = ArrayList<T>()
        val unconfirmed = ArrayList<T>()
        val iterator = held.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.startedAt <= lastFailureAt) {
                unconfirmed += item.value
                iterator.remove()
            } else if (item.finishedAt <= checkStartedAt) {
                confirmed += item.value
                iterator.remove()
            }
        }
        return Release(confirmed, unconfirmed)
    }

    @Synchronized
    fun drain(): List<T> = drainLocked()

    @Synchronized
    fun size(): Int = held.size

    private fun drainLocked(): List<T> = held.map { it.value }.also { held.clear() }
}

internal fun ipv4Address(a: Int, b: Int, c: Int, d: Int): Inet4Address =
    InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())) as Inet4Address

/** Parses a dotted-quad IPv4 literal without any DNS lookup. */
internal fun parseIpv4Literal(value: String?): Inet4Address? {
    val parts = value?.trim()?.split('.') ?: return null
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
        part.toInt().takeIf { it in 0..255 } ?: return null
    }
    return ipv4Address(octets[0], octets[1], octets[2], octets[3])
}
