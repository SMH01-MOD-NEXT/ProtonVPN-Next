/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.utils

import io.sentry.SentryBaseEvent
import io.sentry.Breadcrumb

/** Remove request/identity entirely: SDK capture may include auth headers or HTTP payloads. */
object SentryEventSanitizer {
    fun scrub(event: SentryBaseEvent) {
        event.user = null
        event.request = null
        event.contexts.device?.let { it.name = null; it.id = null }
        // Keep diagnostic SDK contexts; unknown application contexts may contain account data.
        event.contexts.entrySet().map { it.key }.filter { it !in setOf("device", "os", "app", "runtime", "trace", "gpu") }
            .forEach { event.contexts.remove(it) }
        event.contexts.trace?.let { trace ->
            trace.description = PiiScrubber.scrub(trace.description)
            @Suppress("UNCHECKED_CAST")
            val data = PiiScrubber.scrubValue(trace.data) as Map<String, Any>
            trace.data.clear()
            data.forEach { (key, value) -> trace.setData(key, value) }
        }
        @Suppress("UNCHECKED_CAST")
        val extras = PiiScrubber.scrubValue(event.extras) as? Map<String, Any>
        event.extras = extras
        @Suppress("UNCHECKED_CAST")
        val tags = PiiScrubber.scrubValue(event.tags) as? Map<String, String>
        event.tags = tags
        event.breadcrumbs?.forEach(::scrubBreadcrumb)
    }

    fun scrubBreadcrumb(breadcrumb: Breadcrumb) {
        breadcrumb.category = PiiScrubber.scrub(breadcrumb.category)
        breadcrumb.message = PiiScrubber.scrub(breadcrumb.message)
        @Suppress("UNCHECKED_CAST")
        val data = PiiScrubber.scrubValue(breadcrumb.data) as Map<String, Any>
        breadcrumb.data.clear()
        data.forEach { (key, value) -> breadcrumb.setData(key, value) }
    }
}
