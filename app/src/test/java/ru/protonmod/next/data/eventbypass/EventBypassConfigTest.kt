package ru.protonmod.next.data.eventbypass

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry
import ru.protonmod.next.data.model.eventbypass.EventBypassResponse

class EventBypassConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a published event bypass`() {
        val payload = """
            {
              "version": 1,
              "updatedAt": "2026-08-07T11:30:00Z",
              "event": {
                "id": "choreo",
                "name": "Choreo",
                "url": "https://example.choreoapps.dev/api/",
                "enabled": true
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)

        assertEquals(1, parsed.version)
        assertEquals("2026-08-07T11:30:00Z", parsed.updatedAt)
        assertNotNull(parsed.event)
        assertEquals("Choreo", parsed.event?.name)
        assertEquals("https://example.choreoapps.dev/api/", parsed.event?.normalizedUrl())
    }

    @Test
    fun `tolerates unknown fields and a missing event`() {
        val payload = """{ "version": 1, "updatedAt": "", "note": "nothing right now" }"""

        val parsed = json.decodeFromString(EventBypassResponse.serializer(), payload)

        assertNull(parsed.event)
    }

    @Test
    fun `disabled entry yields no url even when one is published`() {
        val entry = EventBypassEntry(
            id = "choreo",
            name = "Choreo",
            url = "https://example.choreoapps.dev/api/",
            enabled = false
        )

        assertEquals("", entry.normalizedUrl())
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
        val entry = EventBypassEntry(name = "Choreo", url = "  https://example.choreoapps.dev/api  ", enabled = true)

        assertEquals("https://example.choreoapps.dev/api/", entry.normalizedUrl())
    }
}
