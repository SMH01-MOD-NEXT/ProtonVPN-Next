package ru.protonmod.next.vpn

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer

class IpRotationSelectorTest {
    private fun server(id: String, country: String, tier: Int = 0, load: Int = 10, status: Int = 1) =
        LogicalServer(
            id = id,
            name = id,
            tier = tier,
            features = 0,
            entryCountry = country,
            exitCountry = country,
            city = "City",
            servers = listOf(PhysicalServer("physical-$id", domain = "$id.test", status = status, wgPublicKey = "key", load = load)),
            averageLoad = load,
        )

    @Test
    fun `same-country rotation excludes current and unavailable servers`() {
        val current = server("current", "NL")
        val selected = IpRotationSelector.select(
            listOf(current, server("offline", "NL", status = 0), server("other", "DE"), server("next", "NL")),
            current,
            maxTier = 0,
            keepCountry = true,
            random = Random(1),
        )
        assertEquals("next", selected?.id)
    }

    @Test
    fun `rotation respects account tier`() {
        val current = server("current", "NL")
        assertNull(IpRotationSelector.select(listOf(current, server("paid", "NL", tier = 2)), current, 0, true, Random(1)))
    }

    @Test
    fun `global rotation may change country`() {
        val current = server("current", "NL")
        val selected = IpRotationSelector.select(listOf(current, server("next", "DE")), current, 0, false, Random(1))
        assertEquals("next", selected?.id)
    }
}
