package ru.protonmod.next.vpn

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.protonmod.next.data.local.WarpPreferences
import ru.protonmod.next.data.repository.CustomConfig
import ru.protonmod.next.data.repository.CustomConfigRules

class WarpLocalSettingsTest {
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val text get() = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\nMTU = 1280\n[Peer]\nPublicKey = $key\nEndpoint = engage.cloudflareclient.com:2408\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\nReserved = 1, 2, 3\n"
    @Test fun `local preferences round trip without secrets or Proton settings`() {
        val p = WarpPreferences(true, "162.159.193.1", true, "vpn.example.com", 500, 3)
        p.validate()
        val encoded = Json.encodeToString(p)
        assertEquals(p, Json.decodeFromString<WarpPreferences>(encoded))
        assertFalse(encoded.contains("PrivateKey")); assertFalse(encoded.contains("proton"))
        assertEquals("vpn.example.com", p.resolvedEndpoint())
        assertEquals(null, WarpPreferences().resolvedEndpoint())
        assertEquals(WarpPreferences(), Json.decodeFromString<WarpPreferences>("{}"))
    }
    @Test fun `invalid port profile or custom endpoint cannot generate`() {
        listOf(WarpPreferences(port = 443), WarpPreferences(profile = 4), WarpPreferences(endpoint = "other.example.com"),
            WarpPreferences(customEndpoint = true, customHost = "[2606:4700:100::1]"), WarpPreferences(customEndpoint = true), WarpPreferences(customEndpoint = true, customHost = "https://example.com")).forEach {
            assertTrue(runCatching { it.validate() }.isFailure)
        }
    }
    @Test fun `regeneration replaces a singleton preserving user configs and stable reference`() {
        val mine = CustomConfig(id = "mine", name = "Mine", link = "untouched")
        val once = CustomConfigRules.replaceWarp(listOf(mine), "first")
        val twice = CustomConfigRules.replaceWarp(once, "second")
        assertEquals(2, twice.size); assertEquals(mine, twice.first())
        assertEquals(1, twice.count { it.isWarp })
        assertEquals("second", CustomConfigRules.resolveChain(twice, "custom://warp", true))
        assertTrue(runCatching { CustomConfigRules.resolveChain(twice, "custom://warp", false) }.isFailure)
        assertTrue(runCatching { CustomConfigRules.resolveChain(listOf(mine), "custom://warp", true) }.isFailure)
    }
    @Test fun `legacy generated WARP configs collapse without breaking previous hop references`() {
        val old = listOf("old-1", "old-2").map { CustomConfig(id = it, name = "WARP", link = ImportedWireGuard.encode(text)) }
        val mine = CustomConfig(name = "WARP", link = "not a generated config")
        val migrated = CustomConfigRules.migrate(old + mine)
        assertEquals(2, migrated.size)
        assertTrue(migrated.contains(mine))
        assertEquals(1, migrated.count { it.isWarp })
        assertEquals(migrated, CustomConfigRules.migrate(migrated))
        val again = CustomConfigRules.replaceWarp(migrated, "replacement")
        assertEquals("replacement", CustomConfigRules.resolveChain(again, "custom://old-1", true))
        assertEquals("replacement", CustomConfigRules.resolveChain(again, "custom://old-2", true))
    }
    @Test fun `legacy schema still reads without kind or aliases`() {
        val config = Json.decodeFromString<CustomConfig>("""{"id":"x","name":"Mine","link":"vless"}""")
        assertFalse(config.isWarp); assertTrue(config.previousIds.isEmpty())
    }
    @Test fun `chain picker appends WARP before destination and rejects duplicates or fifth hop`() {
        val warp = CustomConfig(id = "warp", name = "WARP", link = "secret", kind = "warp", previousIds = listOf("old"))
        assertEquals("vless://first\ncustom://warp", CustomConfigRules.appendHop("vless://first", warp))
        assertTrue(runCatching { CustomConfigRules.appendHop("custom://warp", warp) }.isFailure)
        assertTrue(runCatching { CustomConfigRules.appendHop("custom://old", warp) }.isFailure)
        assertTrue(runCatching { CustomConfigRules.appendHop("a\nb\nc\nd", warp) }.isFailure)
    }
    @Test fun `full store permits singleton replacement but not new overflow`() {
        val full = (1..100).map { CustomConfig(id = "$it", name = "Mine", link = "opaque") }
        assertTrue(runCatching { CustomConfigRules.replaceWarp(full, "new") }.isFailure)
        val withWarp = CustomConfigRules.replaceWarp(full.take(99), "first")
        assertEquals(100, CustomConfigRules.replaceWarp(withWarp, "second").size)
    }
}
