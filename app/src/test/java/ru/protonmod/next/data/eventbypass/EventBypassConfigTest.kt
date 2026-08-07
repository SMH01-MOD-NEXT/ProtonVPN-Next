package ru.protonmod.next.data.eventbypass

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.data.model.eventbypass.EventBypassCache
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry
import ru.protonmod.next.data.model.eventbypass.EventBypassResponse
import ru.protonmod.next.data.model.eventbypass.selectedOrFirst

class EventBypassConfigTest {

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
    fun `the selection survives a refresh and falls back when it disappears`() {
        val entries = listOf(
            EventBypassEntry(id = "choreo", name = "Choreo", url = "https://choreo.example/api/", enabled = true),
            EventBypassEntry(id = "northflank", name = "Northflank", url = "https://nf.example/api/", enabled = true)
        )

        assertEquals("northflank", entries.selectedOrFirst("northflank")?.id)
        // The picked bypass was pulled from the config: fall back to the first one
        // rather than leaving the strategy pointing at nothing.
        assertEquals("choreo", entries.selectedOrFirst("retired")?.id)
        assertEquals("choreo", entries.selectedOrFirst("")?.id)
        assertNull(emptyList<EventBypassEntry>().selectedOrFirst("choreo"))
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
