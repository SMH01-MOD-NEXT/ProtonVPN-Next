/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import ru.protonmod.next.data.network.LogicalServer

/** Scanner verdict for one physical server. */
enum class ServerAvailability { AVAILABLE, BLOCKED, UNKNOWN }

/** Why a verdict was reached. */
enum class ServerProbeDetail {
    /** The server returned a cryptographically valid handshake response. */
    HANDSHAKE_VERIFIED,

    /** The handshake and a DNS round trip through the server both succeeded. */
    DATA_VERIFIED,

    /** The handshake works, but no data passes through the established session. */
    DATA_PLANE_BLOCKED,

    /** The handshake works, but the data check could not be completed. */
    DATA_UNVERIFIED,

    /** No WireGuard reply while the server's IP answers over TCP: the protocol is filtered. */
    PROTOCOL_BLOCKED,

    /** Neither WireGuard nor TCP reach the server's IP address. */
    IP_UNREACHABLE,

    /** No WireGuard reply (TCP check disabled or inconclusive). */
    NO_RESPONSE,

    /** The server only sent cookie replies because it is under load. */
    SERVER_BUSY,

    /** Something answered, but not with a valid WireGuard reply. */
    INVALID_RESPONSE,

    /** The phone's own connection failed while this server was being checked. */
    NO_NETWORK,

    /** The server's host name could not be resolved. */
    DNS_FAILED,

    /** The server list has no usable WireGuard key for this server. */
    NO_PUBLIC_KEY,

    /** A local socket error prevented the check. */
    NETWORK_ERROR,
}

data class ServerProbeResult(
    val physicalServerId: String,
    val logicalServerId: String,
    val availability: ServerAvailability,
    val detail: ServerProbeDetail,
    val endpoint: String?,
    val ports: List<Int>,
    val latencyMs: Int?,
    val checkedAt: Long,
)

/** Badge shown next to servers, cities and countries. */
enum class AvailabilityBadge { ALL_AVAILABLE, PARTIAL, ALL_BLOCKED, UNKNOWN }

object ServerAvailabilityAggregator {

    /**
     * Green when any physical server of [server] answered, red when every checked one is blocked,
     * yellow otherwise; null when nothing was checked yet.
     */
    fun forServer(server: LogicalServer, results: Map<String, ServerProbeResult>): AvailabilityBadge? {
        var checked = 0
        var blocked = 0
        for (physical in server.servers) {
            val result = results[physical.id] ?: continue
            checked++
            when (result.availability) {
                ServerAvailability.AVAILABLE -> return AvailabilityBadge.ALL_AVAILABLE
                ServerAvailability.BLOCKED -> blocked++
                ServerAvailability.UNKNOWN -> Unit
            }
        }
        return when {
            checked == 0 -> null
            blocked == checked -> AvailabilityBadge.ALL_BLOCKED
            else -> AvailabilityBadge.UNKNOWN
        }
    }

    /**
     * Badge for a city or a country. [servers] are the servers a full scan would check there, so
     * green and red are shown only when every one of them was checked: a partial scan never
     * claims that a whole country is available or blocked.
     */
    fun forGroup(servers: Collection<LogicalServer>, serverBadges: Map<String, AvailabilityBadge>): AvailabilityBadge? {
        if (servers.isEmpty()) return null
        var checked = 0
        var available = 0
        var blocked = 0
        for (server in servers) {
            val badge = serverBadges[server.id] ?: continue
            checked++
            if (badge == AvailabilityBadge.ALL_AVAILABLE) available++
            if (badge == AvailabilityBadge.ALL_BLOCKED) blocked++
        }
        return when {
            checked == 0 -> null
            available == servers.size -> AvailabilityBadge.ALL_AVAILABLE
            blocked == servers.size -> AvailabilityBadge.ALL_BLOCKED
            available > 0 -> AvailabilityBadge.PARTIAL
            else -> AvailabilityBadge.UNKNOWN
        }
    }
}
