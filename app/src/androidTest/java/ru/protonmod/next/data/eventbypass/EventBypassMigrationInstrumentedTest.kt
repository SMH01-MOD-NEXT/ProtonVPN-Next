package ru.protonmod.next.data.eventbypass

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.protonmod.next.data.model.eventbypass.EventBypassResponse
import ru.protonmod.next.data.model.eventbypass.selectionForRefresh

@RunWith(AndroidJUnit4::class)
class EventBypassMigrationInstrumentedTest {
    @Test
    fun denoEventCanReplaceLegacyStrategyAndReportsExpiry() {
        val config = Json.decodeFromString<EventBypassResponse>(
            """{"version":2,"events":[{"id":"deno","name":"Deno","url":"https://deno.example/api/","enabled":true,"expiresAt":"01-04-2027"}]}"""
        )
        val selected = requireNotNull(config.usableEvents().selectionForRefresh("", legacyDeno = true))
        assertEquals("deno", selected.stableId())
        val deadline = requireNotNull(selected.expiryMillis())
        assertFalse(selected.isExpired(deadline))
        assertTrue(selected.isExpired(deadline + 1))
    }
}
