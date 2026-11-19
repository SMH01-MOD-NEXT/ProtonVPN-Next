package ru.protonmod.next.data.eventbypass

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.data.model.eventbypass.EventBypassCache
import ru.protonmod.next.data.model.eventbypass.EventBypassDates
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry
import ru.protonmod.next.data.model.eventbypass.EventBypassResponse
import ru.protonmod.next.data.model.eventbypass.selectionForRefresh
import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.protonmod.next.data.model.eventbypass.EventBypassRoute
import ru.protonmod.next.data.model.eventbypass.routeForSelection
import ru.protonmod.next.data.network.ProxyEndpoints

class EventBypassConfigTest {
    @Test
    fun `legacy Deno selection migrates only when a usable Deno event exists`() {
        val vercel = EventBypassEntry(id = "vercel", name = "Vercel", url = "https://v.example/api/", enabled = true)
        val deno = EventBypassEntry(id = "deno", name = "Deno", url = "https://d.example/api/", enabled = true, expiresAt = "forever")
        assertEquals(deno, listOf(vercel, deno).selectionForRefresh("vercel", true))
        assertNull(listOf(vercel).selectionForRefresh("vercel", true))
        assertNull(listOf(deno.copy(enabled = false)).selectionForRefresh("", true))
        assertEquals(vercel, listOf(vercel, deno).selectionForRefresh("vercel", false))
    }


    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses several published bypasses in order`() {
        val payload = """
            {
              "version": 2,
              "updatedAt": "2026-08-07T14:45:00Z",
              "events": [
                { "id": "choreo", "name": "Choreo", "url": "https://choreo.example/api/", "enabled": true },
                { "id": "northflank", "name": "Northflank", "url": "https://nf.example/api/", "enabled": true }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)
        val usable = parsed.usableEvents()

        assertEquals(2, parsed.version)
        assertEquals(listOf("Choreo", "Northflank"), usable.map { it.name })
        assertEquals("https://choreo.example/api/", usable.first().normalizedUrl())
    }

    @Test
    fun `disabled and plaintext entries are dropped from the published list`() {
        val payload = """
            {
              "version": 2,
              "updatedAt": "2026-08-07T14:45:00Z",
              "events": [
                { "id": "parked", "name": "Parked", "url": "https://parked.example/api/", "enabled": false },
                { "id": "plaintext", "name": "Plaintext", "url": "http://insecure.example/api/", "enabled": true },
                { "id": "live", "name": "Live", "url": "https://live.example/api/", "enabled": true }
              ]
            }
        """.trimIndent()

        val usable = json.decodeFromString(EventBypassResponse.serializer(), payload).usableEvents()

        assertEquals(listOf("live"), usable.map { it.id })
    }

    @Test
    fun `a version 1 config with a single event still works`() {
        val payload = """
            {
              "version": 1,
              "updatedAt": "2026-08-07T11:30:00Z",
              "event": { "id": "choreo", "name": "Choreo", "url": "https://choreo.example/api/", "enabled": true }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)

        assertNotNull(parsed.event)
        assertEquals(listOf("Choreo"), parsed.usableEvents().map { it.name })
    }

    @Test
    fun `a legacy event duplicating a list entry is not offered twice`() {
        val payload = """
            {
              "version": 2,
              "updatedAt": "2026-08-07T14:45:00Z",
              "events": [
                { "id": "choreo", "name": "Choreo", "url": "https://choreo.example/api/", "enabled": true }
              ],
              "event": { "id": "choreo", "name": "Choreo", "url": "https://choreo.example/api/", "enabled": true }
            }
        """.trimIndent()

        assertEquals(1, json.decodeFromString(EventBypassResponse.serializer(), payload).usableEvents().size)
    }

    @Test
    fun `tolerates unknown fields and a config with nothing published`() {
        val payload = """{ "version": 2, "updatedAt": "", "note": "nothing right now" }"""

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)

        assertNull(parsed.event)
        assertTrue(parsed.usableEvents().isEmpty())
    }

    @Test
    fun `plaintext and empty urls are refused`() {
        val plaintext = EventBypassEntry(name = "Bad", url = "http://example.com/api/", enabled = true)
        val empty = EventBypassEntry(name = "None", url = "", enabled = true)

        assertEquals("", plaintext.normalizedUrl())
        assertEquals("", empty.normalizedUrl())
    }

    @Test
    fun `a trailing slash is added so the url can be used as a base url`() {
        val entry = EventBypassEntry(name = "Choreo", url = "  https://choreo.example/api  ", enabled = true)

        assertEquals("https://choreo.example/api/", entry.normalizedUrl())
    }

    @Test
    fun `refresh keeps expired selection or waits for user when selected id is missing`() {
        val expired = EventBypassEntry(id = "trial", url = "https://trial.example/api/", enabled = true, expiresAt = "01-12-2026")
        val live = EventBypassEntry(id = "live", url = "https://live.example/api/", enabled = true)
        val now = requireNotNull(expired.expiryMillis()) + 1
        assertEquals(expired, listOf(expired, live).selectionForRefresh("trial", false, now))
        assertNull(listOf(live).selectionForRefresh("trial", false, now))
        assertEquals(live, listOf(expired, live).selectionForRefresh("", false, now))
        assertNull(listOf(expired.copy(id = "deno")).selectionForRefresh("", true, now))
    }

    @Test
    fun `expired Event routes through Cloudflare using its path contract`() {
        val trial = EventBypassEntry(id = "trial", url = "https://trial.example/api/", enabled = true,
            expiresAt = "01-12-2026", routing = "query")
        val deadline = requireNotNull(trial.expiryMillis())
        val live = trial.copy(id = "live", expiresAt = "forever", routing = "path")
        val entries = listOf(trial, live)
        assertEquals(EventBypassRoute(trial.normalizedUrl(), "query"), entries.routeForSelection("trial", deadline))
        val fallback = entries.routeForSelection("trial", deadline + 1)
        assertEquals(EventBypassRoute(ProxyEndpoints.CLOUDFLARE_URL, "path"), fallback)
        val url = ProxyEndpoints.apiUrl("https://vpn-api.proton.me/auth/v4".toHttpUrl(), fallback.url.toHttpUrl(), fallback.routing)
        assertEquals("/api/auth/v4", url.encodedPath)
        assertNull(url.queryParameter("__path"))
        assertEquals(EventBypassRoute(live.normalizedUrl(), "path"), entries.routeForSelection("live", deadline + 1))
        val refreshed = listOf(trial.copy(expiresAt = "forever"), live)
        assertEquals(EventBypassRoute(trial.normalizedUrl(), "query"), refreshed.routeForSelection("trial", deadline + 1))
    }

    @Test
    fun `unavailable Event falls back without selecting another cached endpoint`() {
        val fallback = EventBypassRoute(ProxyEndpoints.CLOUDFLARE_URL, "path")
        val live = EventBypassEntry(id = "live", url = "https://live.example/api/", enabled = true)
        assertEquals(fallback, emptyList<EventBypassEntry>().routeForSelection(""))
        assertEquals(fallback, listOf(live).routeForSelection(""))
        assertEquals(fallback, listOf(live).routeForSelection("removed"))
        assertEquals(fallback, listOf(live.copy(enabled = false)).routeForSelection("live"))
        assertEquals(fallback, listOf(live.copy(url = "http://live.example/api/")).routeForSelection("live"))
    }

    @Test
    fun `an expiry date is read as the end of that day`() {
        val entry = EventBypassEntry(name = "Trial", url = "https://t.example/api/", enabled = true, expiresAt = "01-12-2026")
        val expiry = entry.expiryMillis()

        assertNotNull(expiry)
        // Midday on the announced date is still inside the trial: the date names a
        // whole day, not the moment it starts.
        val noonThatDay = expiry!! - 12 * 60 * 60 * 1000
        assertTrue(!entry.isExpired(noonThatDay))
        assertTrue(entry.isExpired(expiry + 1))
        assertTrue(!entry.neverExpires())
    }

    @Test
    fun `forever unknown and malformed dates never count as expired`() {
        val forever = EventBypassEntry(name = "Stable", url = "https://s.example/api/", enabled = true, expiresAt = "forever")
        val unknown = EventBypassEntry(name = "Unknown", url = "https://u.example/api/", enabled = true)
        // A swapped day and month would otherwise silently retire a live bypass.
        val malformed = EventBypassEntry(name = "Typo", url = "https://x.example/api/", enabled = true, expiresAt = "2026-12-01")
        val impossible = EventBypassEntry(name = "Impossible", url = "https://y.example/api/", enabled = true, expiresAt = "32-13-2026")

        assertTrue(forever.neverExpires())
        assertNull(forever.expiryMillis())
        assertNull(unknown.expiryMillis())
        assertNull(malformed.expiryMillis())
        assertNull(impossible.expiryMillis())
        listOf(forever, unknown, malformed, impossible).forEach { assertTrue(!it.isExpired()) }
    }

    @Test
    fun `the published timestamp is parsed and kept in the cache`() {
        val payload = """
            {
              "version": 2,
              "updatedAt": "2026-08-07T15:20:00Z",
              "events": [
                { "id": "choreo", "name": "Choreo", "url": "https://choreo.example/api/", "enabled": true, "expiresAt": "01-12-2026" }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)

        // 2026-08-07T15:20:00Z, parsed as UTC no matter what the device timezone is.
        assertEquals(1_786_116_000_000L, EventBypassDates.parseIsoTimestamp(parsed.updatedAt))
        assertEquals("01-12-2026", parsed.usableEvents().first().expiresAt)
        assertNull(EventBypassDates.parseIsoTimestamp(""))
        assertNull(EventBypassDates.parseIsoTimestamp("whenever"))
    }

    @Test
    fun `the cached list round-trips and a corrupt cache is ignored`() {
        val entries = listOf(
            EventBypassEntry(id = "choreo", name = "Choreo", url = "https://choreo.example/api/", enabled = true)
        )

        assertEquals(entries, EventBypassCache.decode(EventBypassCache.encode(entries)))
        assertTrue(EventBypassCache.decode("not json at all").isEmpty())
        assertTrue(EventBypassCache.decode("").isEmpty())
    }
}
