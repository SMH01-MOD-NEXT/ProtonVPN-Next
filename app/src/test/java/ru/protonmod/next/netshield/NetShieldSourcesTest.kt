package ru.protonmod.next.netshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetShieldSourcesTest {

    @Test
    fun `categories fall back to the built-in defaults`() {
        val config = NetShieldSourceConfig()

        NetShieldSources.downloadableCategories.forEach { category ->
            assertEquals(NetShieldSources.defaults.getValue(category), NetShieldSources.resolve(category, config))
        }
    }

    @Test
    fun `custom list is not a downloadable category`() {
        assertFalse(NetShieldCategory.CUSTOM in NetShieldSources.downloadableCategories)
    }

    @Test
    fun `a preset replaces the default of one category only`() {
        val config = NetShieldSourceConfig().withPreset(NetShieldCategory.TRACKERS, "hagezi-pro")

        assertEquals(
            NetShieldSources.preset("hagezi-pro")!!.url,
            NetShieldSources.resolve(NetShieldCategory.TRACKERS, config)
        )
        assertEquals(
            NetShieldSources.defaults.getValue(NetShieldCategory.ADS),
            NetShieldSources.resolve(NetShieldCategory.ADS, config)
        )
    }

    @Test
    fun `a user url wins over a preset and is cleared by choosing a preset`() {
        val withUrl = NetShieldSourceConfig()
            .withPreset(NetShieldCategory.ADS, "adguard-dns")
            .withCustomUrl(NetShieldCategory.ADS, " https://example.com/hosts.txt ")

        assertEquals("https://example.com/hosts.txt", NetShieldSources.resolve(NetShieldCategory.ADS, withUrl))

        val backToPreset = withUrl.withPreset(NetShieldCategory.ADS, "adaway")
        assertEquals(
            NetShieldSources.preset("adaway")!!.url,
            NetShieldSources.resolve(NetShieldCategory.ADS, backToPreset)
        )
    }

    @Test
    fun `presets are only offered for supported categories`() {
        assertTrue(NetShieldSources.presetsFor(NetShieldCategory.ADULT).all { NetShieldCategory.ADULT in it.categories })
        assertTrue(NetShieldSources.universalPresets().all { preset ->
            NetShieldSources.downloadableCategories.all { it in preset.categories }
        })
    }

    @Test
    fun `apply to all switches every supported category`() {
        val config = NetShieldSources.applyToAll(NetShieldSourceConfig(), "oisd-big")
        val expected = NetShieldSources.preset("oisd-big")!!.url

        NetShieldSources.downloadableCategories.forEach { category ->
            assertEquals(expected, NetShieldSources.resolve(category, config))
        }
    }

    @Test
    fun `reset returns a category to its default`() {
        val config = NetShieldSourceConfig()
            .withCustomUrl(NetShieldCategory.MALWARE, "https://example.com/malware.txt")
            .reset(NetShieldCategory.MALWARE)

        assertEquals(
            NetShieldSources.defaults.getValue(NetShieldCategory.MALWARE),
            NetShieldSources.resolve(NetShieldCategory.MALWARE, config)
        )
    }

    @Test
    fun `config survives a serialization round trip`() {
        val config = NetShieldSourceConfig()
            .withPreset(NetShieldCategory.TRACKERS, "hagezi-pro")
            .withCustomUrl(NetShieldCategory.ADS, "https://example.com/hosts.txt")

        assertEquals(config, NetShieldSources.decode(NetShieldSources.encode(config)))
        assertEquals(NetShieldSourceConfig(), NetShieldSources.decode("broken"))
    }

    @Test
    fun `fingerprint changes when a source changes`() {
        val defaults = NetShieldSourceConfig()
        val changed = defaults.withPreset(NetShieldCategory.ADS, "oisd-small")

        assertEquals(NetShieldSources.fingerprint(defaults), NetShieldSources.fingerprint(NetShieldSourceConfig()))
        assertNotEquals(NetShieldSources.fingerprint(defaults), NetShieldSources.fingerprint(changed))
    }

    @Test
    fun `only absolute urls are accepted`() {
        assertTrue(NetShieldSources.isValidUrl("https://example.com/hosts.txt"))
        assertFalse(NetShieldSources.isValidUrl("example.com/hosts.txt"))
        assertFalse(NetShieldSources.isValidUrl(""))
    }

    @Test
    fun `user rules are parsed from mixed list formats`() {
        val parsed = NetShieldDomainParser.parse(
            """
            # comment
            0.0.0.0 ads.example.com
            ||tracker.example.net^
            plain.example.org
            not a domain
            """.trimIndent()
        )

        assertEquals(setOf("ads.example.com", "tracker.example.net", "plain.example.org"), parsed)
    }
}
