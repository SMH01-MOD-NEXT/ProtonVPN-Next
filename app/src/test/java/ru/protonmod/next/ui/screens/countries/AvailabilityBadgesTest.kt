/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.countries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.network.probe.AvailabilityBadge
import ru.protonmod.next.data.network.probe.ServerAvailability
import ru.protonmod.next.data.network.probe.ServerProbeDetail
import ru.protonmod.next.data.network.probe.ServerProbeResult

class AvailabilityBadgesTest {

    private fun physical(id: String, status: Int = 1) = PhysicalServer(id = id, domain = id + ".example", status = status)

    private fun logical(id: String, country: String, city: String, physical: List<PhysicalServer>, tier: Int = 0) =
        LogicalServer(
            id = id, name = id, tier = tier, features = 0,
            entryCountry = country, exitCountry = country, city = city, servers = physical,
        )

    private fun result(physicalId: String, logicalId: String, availability: ServerAvailability) = ServerProbeResult(
        physicalServerId = physicalId,
        logicalServerId = logicalId,
        availability = availability,
        detail = ServerProbeDetail.NO_RESPONSE,
        endpoint = null,
        ports = listOf(443),
        latencyMs = null,
        checkedAt = 1L,
    )

    @Test
    fun emptyResultsGiveNoBadges() {
        val servers = listOf(logical("a", "DE", "Berlin", listOf(physical("a1"))))
        assertEquals(AvailabilityBadges.EMPTY, AvailabilityBadges.build(servers, emptyMap(), 0))
    }

    @Test
    fun serverIsAvailableWhenAnyAddressAnswers() {
        val server = logical("a", "DE", "Berlin", listOf(physical("a1"), physical("a2")))
        val badges = AvailabilityBadges.build(
            listOf(server),
            mapOf(
                "a1" to result("a1", "a", ServerAvailability.BLOCKED),
                "a2" to result("a2", "a", ServerAvailability.AVAILABLE),
            ),
            0,
        )
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.server("a"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.country("DE"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.city("DE", "Berlin"))
    }

    @Test
    fun countryIsPartialWhenSomeServersAreBlocked() {
        val servers = listOf(
            logical("a", "DE", "Berlin", listOf(physical("a1"))),
            logical("b", "DE", "Frankfurt", listOf(physical("b1"))),
        )
        val badges = AvailabilityBadges.build(
            servers,
            mapOf(
                "a1" to result("a1", "a", ServerAvailability.AVAILABLE),
                "b1" to result("b1", "b", ServerAvailability.BLOCKED),
            ),
            0,
        )
        assertEquals(AvailabilityBadge.PARTIAL, badges.country("DE"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.city("DE", "Berlin"))
        assertEquals(AvailabilityBadge.ALL_BLOCKED, badges.city("DE", "Frankfurt"))
        assertEquals(AvailabilityBadge.ALL_BLOCKED, badges.server("b"))
    }

    @Test
    fun countryIsBlockedOnlyWhenEveryServerIsBlocked() {
        val servers = listOf(
            logical("a", "RU", "Moscow", listOf(physical("a1"))),
            logical("b", "RU", "Moscow", listOf(physical("b1"))),
        )
        val allBlocked = AvailabilityBadges.build(
            servers,
            mapOf(
                "a1" to result("a1", "a", ServerAvailability.BLOCKED),
                "b1" to result("b1", "b", ServerAvailability.BLOCKED),
            ),
            0,
        )
        assertEquals(AvailabilityBadge.ALL_BLOCKED, allBlocked.country("RU"))
        assertEquals(AvailabilityBadge.ALL_BLOCKED, allBlocked.city("RU", "Moscow"))

        val oneUnchecked = AvailabilityBadges.build(servers, mapOf("a1" to result("a1", "a", ServerAvailability.BLOCKED)), 0)
        assertEquals(AvailabilityBadge.UNKNOWN, oneUnchecked.country("RU"))
        assertNull(oneUnchecked.server("b"))
    }

    @Test
    fun unverifiedServerShowsYellowDot() {
        val server = logical("a", "NL", "Amsterdam", listOf(physical("a1")))
        val badges = AvailabilityBadges.build(listOf(server), mapOf("a1" to result("a1", "a", ServerAvailability.UNKNOWN)), 0)
        assertEquals(AvailabilityBadge.UNKNOWN, badges.server("a"))
        assertEquals(AvailabilityBadge.UNKNOWN, badges.country("NL"))
    }

    @Test
    fun serversOutsideTheScanDoNotKeepGroupsFromTurningGreen() {
        val servers = listOf(
            logical("free", "CH", "Zurich", listOf(physical("f1"))),
            logical("plus", "CH", "Zurich", listOf(physical("p1")), tier = 2),
            logical("offline", "CH", "Zurich", listOf(physical("o1", status = 0))),
        )
        val badges = AvailabilityBadges.build(servers, mapOf("f1" to result("f1", "free", ServerAvailability.AVAILABLE)), 0)
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.country("CH"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, badges.city("CH", "Zurich"))
        assertNull(badges.server("plus"))
    }
}
