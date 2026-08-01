package ru.protonmod.next.ui.screens.countries

import kotlinx.serialization.Serializable
import ru.protonmod.next.data.network.LogicalServer

@Serializable
data class MultiHopTarget(
    val kind: String,
    val countryCode: String,
    val city: String = "",
    val serverId: String = "",
    val serverName: String = ""
)

@Serializable
data class MultiHopProfile(
    val id: String,
    val entry: MultiHopTarget,
    val exit: MultiHopTarget
)

internal fun resolveMultiHopTarget(target: MultiHopTarget, servers: List<LogicalServer>): LogicalServer? {
    val candidates = when (target.kind) {
        "server" -> servers.filter { it.id == target.serverId }
        "city" -> servers.filter { it.exitCountry == target.countryCode && it.city == target.city }
        else -> servers.filter { it.exitCountry == target.countryCode }
    }
    return candidates.filter { logical -> logical.servers.any { it.status == 1 } }
        .minByOrNull { it.averageLoad }
        ?: candidates.minByOrNull { it.averageLoad }
}

internal fun buildMultiHopTargets(servers: List<LogicalServer>): List<MultiHopTarget> = buildList {
    servers.map { it.exitCountry }.distinct().sorted().forEach { add(MultiHopTarget("country", it)) }
    servers.groupBy { it.exitCountry to it.city }.keys
        .sortedWith(compareBy({ it.first }, { it.second }))
        .forEach { (country, city) -> add(MultiHopTarget("city", country, city)) }
    servers.sortedBy { it.name }.forEach { add(MultiHopTarget("server", it.exitCountry, it.city, it.id, it.name)) }
}
