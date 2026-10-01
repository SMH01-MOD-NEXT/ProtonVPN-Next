/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.utils

import io.sentry.SentryEvent
import io.sentry.Breadcrumb
import io.sentry.protocol.Request
import io.sentry.protocol.User
import org.junit.Assert.*
import org.junit.Test

class SentryEventSanitizerTest {
    @Test fun `request identity and nested secrets never reach an event`() {
        val event = SentryEvent().apply {
            user = User().apply { ipAddress = "192.0.2.1"; email = "alice@example.org" }
            request = Request().apply { headers = mapOf("Authorization" to "Bearer secret") }
            setExtra("nested", mapOf("AccessToken" to "short", "items" to listOf("2001:db8::1")))
            setTag("sessionId", "short")
            addBreadcrumb(Breadcrumb().apply {
                message = "alice@example.org"
                setData("request", mapOf("Cookie" to "session=short"))
            })
        }
        SentryEventSanitizer.scrub(event)
        assertNull(event.user)
        assertNull(event.request)
        assertEquals("[REDACTED]", event.tags!!["sessionId"])
        assertFalse(event.extras.toString().contains("short"))
        assertFalse(event.extras.toString().contains("2001:db8"))
        assertEquals("[EMAIL]", event.breadcrumbs!![0].message)
        assertFalse(event.breadcrumbs!![0].data.toString().contains("session=short"))
    }
}
