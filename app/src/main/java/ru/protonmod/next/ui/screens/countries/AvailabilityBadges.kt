/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.countries

import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.probe.AvailabilityBadge
import ru.protonmod.next.data.network.probe.ServerAvailabilityAggregator
import ru.protonmod.next.data.network.probe.ServerProbeResult
import ru.protonmod.next.data.network.probe.ServerScanEngine

/** Server scanner badges of the countries tab, keyed by country code, city and logical server id. */
data class AvailabilityBadges(
    val countries: Map<String, AvailabilityBadge> = emptyMap(),
    val cities: Map<String, AvailabilityBadge> = emptyMap(),
    val servers: Map<String, AvailabilityBadge> = emptyMap(),
) {
    fun country(code: String): AvailabilityBadge? = countries[code]

    fun city(countryCode: String, city: String): AvailabilityBadge? = cities[cityKey(countryCode, city)]

    fun server(id: String): AvailabilityBadge? = servers[id]

    companion object {
        val EMPTY = AvailabilityBadges()

        fun cityKey(countryCode: String, city: String): String = countryCode + "|" + city

        /**
         * A server gets a badge as soon as one of its addresses was checked. Cities and countries
         * only count the servers that a full scan checks for [userTier], so servers of a higher
         * plan or offline servers never keep a country from turning green.
         */
        fun build(
            servers: List<LogicalServer>,
            results: Map<String, ServerProbeResult>,
            userTier: Int,
        ): AvailabilityBadges {
            if (results.isEmpty() || servers.isEmpty()) return EMPTY
            val serverBadges = HashMap<String, AvailabilityBadge>()
            for (server in servers) {
                ServerAvailabilityAggregator.forServer(server, results)?.let { serverBadges[server.id] = it }
            }
            if (serverBadges.isEmpty()) return EMPTY

            val scannable = servers.filter { ServerScanEngine.isScannable(it, userTier) }
            val countries = HashMap<String, AvailabilityBadge>()
            for ((code, group) in scannable.groupBy { it.exitCountry }) {
                ServerAvailabilityAggregator.forGroup(group, serverBadges)?.let { countries[code] = it }
            }
            val cities = HashMap<String, AvailabilityBadge>()
            for ((key, group) in scannable.groupBy { cityKey(it.exitCountry, it.city) }) {
                ServerAvailabilityAggregator.forGroup(group, serverBadges)?.let { cities[key] = it }
            }
            return AvailabilityBadges(countries, cities, serverBadges)
        }
    }
}
