package ru.protonmod.next.ui.screens.countries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer

class MultiHopProfileTest {
    private fun server(id: String, country: String, city: String, load: Int) = LogicalServer(
        id, id, 0, 0, country, country, city,
        listOf(PhysicalServer("p-$id", domain = "$id.test", status = 1, wgPublicKey = "key", load = load)),
        averageLoad = load
    )

    @Test fun `country and city targets select fastest server`() {
        val servers = listOf(server("slow", "NL", "A", 80), server("fast", "NL", "B", 10), server("city", "NL", "A", 20))
        assertEquals("fast", resolveMultiHopTarget(MultiHopTarget("country", "NL"), servers)?.id)
        assertEquals("city", resolveMultiHopTarget(MultiHopTarget("city", "NL", "A"), servers)?.id)
    }

    @Test fun `profiles survive json round trip`() {
        val profile = MultiHopProfile("id", MultiHopTarget("country", "NL"), MultiHopTarget("city", "CH", "Zurich"))
        assertEquals(listOf(profile), Json.decodeFromString<List<MultiHopProfile>>(Json.encodeToString(listOf(profile))))
    }
}
