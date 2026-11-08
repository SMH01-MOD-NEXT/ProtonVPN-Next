/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ServerScanEngineTest {

    private fun physical(id: String, status: Int = 1) = PhysicalServer(id = id, domain = id + ".example", status = status)

    private fun logical(id: String, country: String, physical: List<PhysicalServer>, tier: Int = 0) = LogicalServer(
        id = id,
        name = id,
        tier = tier,
        features = 0,
        entryCountry = country,
        exitCountry = country,
        city = "City",
        servers = physical,
    )

    private val tcpMustNotRun: () -> TcpReachability? = { throw AssertionError("TCP check is only for silent servers") }

    private fun verified(dataPlane: WireGuardProber.DataPlane) =
        WireGuardProber.Outcome.Verified(listOf(443), 443, 12, dataPlane)

    @Test
    fun verifiedHandshakesAreAvailableUnlessDataIsBlocked() {
        val expected = mapOf(
            WireGuardProber.DataPlane.NOT_CHECKED to
                ProbeVerdict(ServerAvailability.AVAILABLE, ServerProbeDetail.HANDSHAKE_VERIFIED, needsBaseline = false),
            WireGuardProber.DataPlane.VERIFIED to
                ProbeVerdict(ServerAvailability.AVAILABLE, ServerProbeDetail.DATA_VERIFIED, needsBaseline = false),
            WireGuardProber.DataPlane.INCONCLUSIVE to
                ProbeVerdict(ServerAvailability.UNKNOWN, ServerProbeDetail.DATA_UNVERIFIED, needsBaseline = false),
            WireGuardProber.DataPlane.BLOCKED to
                ProbeVerdict(ServerAvailability.BLOCKED, ServerProbeDetail.DATA_PLANE_BLOCKED, needsBaseline = true),
        )
        for ((dataPlane, verdict) in expected) {
            assertEquals(verdict, ServerScanEngine.classify(verified(dataPlane), tcpMustNotRun))
        }
    }

    @Test
    fun silentServersAreBlockedAndTheTcpCheckNamesTheReason() {
        val expected = mapOf(
            TcpReachability.REACHABLE to ServerProbeDetail.PROTOCOL_BLOCKED,
            TcpReachability.UNREACHABLE to ServerProbeDetail.IP_UNREACHABLE,
            TcpReachability.UNKNOWN to ServerProbeDetail.NO_RESPONSE,
            null to ServerProbeDetail.NO_RESPONSE,
        )
        for ((reachability, detail) in expected) {
            assertEquals(
                ProbeVerdict(ServerAvailability.BLOCKED, detail, needsBaseline = true),
                ServerScanEngine.classify(WireGuardProber.Outcome.NoResponse) { reachability },
            )
        }
    }

    @Test
    fun otherOutcomesAreUnknownAndNeedNoBaseline() {
        val expected = mapOf(
            WireGuardProber.Outcome.CookieOnly to ServerProbeDetail.SERVER_BUSY,
            WireGuardProber.Outcome.InvalidResponse to ServerProbeDetail.INVALID_RESPONSE,
            WireGuardProber.Outcome.InvalidKey to ServerProbeDetail.NO_PUBLIC_KEY,
            WireGuardProber.Outcome.Failed("SocketException") to ServerProbeDetail.NETWORK_ERROR,
        )
        for ((outcome, detail) in expected) {
            assertEquals(
                ProbeVerdict(ServerAvailability.UNKNOWN, detail, needsBaseline = false),
                ServerScanEngine.classify(outcome, tcpMustNotRun),
            )
        }
    }

    @Test
    fun onlyAllowedServersWithAnOnlineNodeAreScannable() {
        assertTrue(ServerScanEngine.isScannable(logical("a", "DE", listOf(physical("a1", status = 0), physical("a2"))), 0))
        assertFalse(ServerScanEngine.isScannable(logical("b", "DE", listOf(physical("b1", status = 0))), 2))
        assertFalse(ServerScanEngine.isScannable(logical("c", "DE", listOf(physical("c1")), tier = 2), 0))
        assertTrue(ServerScanEngine.isScannable(logical("c", "DE", listOf(physical("c1")), tier = 2), 2))
    }

    @Test
    fun targetsFollowScopeTierStatusAndAreDeduplicated() {
        val shared = physical("shared")
        val servers = listOf(
            logical("de1", "DE", listOf(physical("de1a"), physical("de1b", status = 0), shared)),
            logical("de2", "de", listOf(shared, physical("de2a"))),
            logical("ch1", "CH", listOf(physical("ch1a")), tier = 2),
            logical("nl1", "NL", listOf(physical("nl1a"))),
        )
        fun ids(scope: ServerScanScope, tier: Int = 0) =
            ServerScanEngine.selectTargets(servers, scope, tier).map { it.logical.id + "/" + it.physical.id }

        assertEquals(listOf("de1/de1a", "de1/shared", "de2/de2a", "nl1/nl1a"), ids(ServerScanScope.All))
        assertEquals(
            listOf("de1/de1a", "de1/shared", "de2/de2a", "ch1/ch1a", "nl1/nl1a"),
            ids(ServerScanScope.All, tier = 2),
        )
        assertEquals(listOf("de1/de1a", "de1/shared", "de2/de2a"), ids(ServerScanScope.Country("de")))
        assertEquals(emptyList<String>(), ids(ServerScanScope.Country("CH")))
        assertEquals(listOf("ch1/ch1a"), ids(ServerScanScope.Country("ch"), tier = 2))
        assertEquals(listOf("de2/shared", "de2/de2a"), ids(ServerScanScope.Server("de2")))
        assertEquals(emptyList<String>(), ids(ServerScanScope.Server("missing")))
    }

    @Test
    fun runAllProcessesEveryItemOnceWithBoundedParallelism() = runTest {
        val items = (1..25).toList()
        val processed = Collections.synchronizedList(ArrayList<Int>())
        val running = AtomicInteger()
        val peak = AtomicInteger()
        ServerScanEngine.runAll(items, 4, StandardTestDispatcher(testScheduler)) { item ->
            peak.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
            delay(10)
            processed += item
            running.decrementAndGet()
        }
        assertEquals(items, processed.sorted())
        assertEquals(4, peak.get())
    }

    @Test
    fun runAllWithoutParallelismRunsOneAtATime() = runTest {
        val processed = Collections.synchronizedList(ArrayList<Int>())
        val running = AtomicInteger()
        val peak = AtomicInteger()
        ServerScanEngine.runAll(listOf(1, 2, 3), 0, StandardTestDispatcher(testScheduler)) { item ->
            peak.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
            delay(10)
            processed += item
            running.decrementAndGet()
        }
        assertEquals(listOf(1, 2, 3), processed.sorted())
        assertEquals(1, peak.get())
    }

    @Test
    fun runAllWithNoItemsDoesNothing() = runTest {
        ServerScanEngine.runAll(emptyList<Int>(), 4, StandardTestDispatcher(testScheduler)) {
            throw AssertionError("no work expected")
        }
    }

    @Test
    fun blockedVerdictNeedsACheckThatStartedAfterTheProbeFinished() {
        val gate = BaselineGate<String>()
        gate.hold(0, 10, "a")
        assertEquals(BaselineGate.Release(emptyList(), emptyList<String>()), gate.onBaseline(true, 5, 15))
        assertEquals(1, gate.size())
        assertEquals(BaselineGate.Release(listOf("a"), emptyList()), gate.onBaseline(true, 10, 20))
        assertEquals(0, gate.size())
    }

    @Test
    fun failedCheckReleasesEverythingAsUnconfirmed() {
        val gate = BaselineGate<String>()
        gate.hold(0, 10, "a")
        gate.hold(5, 12, "b")
        assertEquals(BaselineGate.Release(emptyList(), listOf("a", "b")), gate.onBaseline(false, 20, 30))
        assertEquals(0, gate.size())
    }

    @Test
    fun probesOverlappingAFailureStayUnconfirmed() {
        val gate = BaselineGate<String>()
        gate.onBaseline(false, 40, 50)
        gate.hold(45, 60, "overlaps")
        gate.hold(51, 60, "after")
        assertEquals(BaselineGate.Release(listOf("after"), listOf("overlaps")), gate.onBaseline(true, 70, 80))
    }

    @Test
    fun drainReturnsHeldVerdictsInOrder() {
        val gate = BaselineGate<Int>()
        (1..3).forEach { gate.hold(it.toLong(), it + 10L, it) }
        assertEquals(3, gate.size())
        assertEquals(listOf(1, 2, 3), gate.drain())
        assertEquals(0, gate.size())
        assertEquals(emptyList<Int>(), gate.drain())
    }

    @Test
    fun ipv4LiteralsAreParsedWithoutLookups() {
        assertEquals("10.2.0.1", parseIpv4Literal("10.2.0.1")?.hostAddress)
        assertEquals("1.2.3.4", parseIpv4Literal(" 1.2.3.4 ")?.hostAddress)
        assertEquals("255.255.255.255", parseIpv4Literal("255.255.255.255")?.hostAddress)
        assertEquals("1.2.3.4", parseIpv4Literal("001.2.3.4")?.hostAddress)
        assertEquals("10.2.0.255", ipv4Address(10, 2, 0, 255).hostAddress)
        val invalid = listOf(
            null, "", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.-4", "1..2.3", "1234.1.1.1",
            "1.2.3.4a", "0x1.2.3.4", "example.com", "::1", "1.2.3.4 5",
        )
        for (value in invalid) {
            assertNull(value.toString(), parseIpv4Literal(value))
        }
    }
}
