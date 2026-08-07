/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.model.eventbypass

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shape of `event-bypass.json`, published by the website and mirrored on every
 * host we deploy to. It lists the temporary ("event") proxies that are currently
 * available, so the platforms behind the Event strategy can change without
 * shipping a new APK.
 */
@Serializable
data class EventBypassResponse(
    val version: Int = 1,
    val updatedAt: String = "",
    /** Several bypasses can be published at once; the user picks one in the app. */
    val events: List<EventBypassEntry> = emptyList(),
    /** Version 1 published a single bypass. Still read so older files keep working. */
    val event: EventBypassEntry? = null
) {
    /**
     * Every published bypass the app can actually use, in publication order.
     * Entries with the same id collapse into the first one, so a legacy `event`
     * duplicating a list entry does not show up twice.
     */
    fun usableEvents(): List<EventBypassEntry> =
        (events + listOfNotNull(event))
            .filter { it.normalizedUrl().isNotEmpty() }
            .distinctBy { it.stableId() }
}

@Serializable
data class EventBypassEntry(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = false,
    /**
     * When the platform behind this bypass is expected to stop working, as
     * `dd-MM-yyyy`, or "forever" when it does not run on a trial. Empty means
     * nobody knows, which is different from "forever" and is shown as neither.
     */
    val expiresAt: String = ""
) {
    /**
     * Identifies the entry across refreshes so the user's choice survives them.
     * `id` is what the website is supposed to set; the fallbacks only keep a
     * sloppily edited config usable instead of silently dropping the entry.
     */
    fun stableId(): String = id.trim().ifBlank { name.trim().ifBlank { url.trim() } }

    /**
     * Returns the URL the interceptor can use as a base URL, or an empty string
     * when the entry is not usable. Plaintext endpoints are refused: the whole
     * point of the bypass is to carry API traffic, and the certificate checks
     * downstream assume HTTPS.
     */
    fun normalizedUrl(): String {
        if (!enabled) return ""
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /** True when the config says this platform is not running on a trial. */
    fun neverExpires(): Boolean =
        expiresAt.trim().equals(EventBypassDates.FOREVER, ignoreCase = true)

    /** End of the last day this bypass is expected to work, or null when unknown. */
    fun expiryMillis(): Long? = EventBypassDates.parseExpiryDay(expiresAt)

    /**
     * Whether the announced date has passed. The date is informational: it is typed
     * by hand and read against a clock the user can change, so an expired entry is
     * still offered and still routable — it is only labelled as expired.
     */
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiryMillis() ?: return false
        return nowMillis > expiry
    }
}

/**
 * Date handling for the config. The published dates come in two different shapes
 * on purpose: `updatedAt` is machine-written by the website and is ISO-8601, while
 * `expiresAt` is typed by a human and is the far friendlier `dd-MM-yyyy`.
 */
object EventBypassDates {
    /** Value of `expiresAt` meaning the platform is not on a trial. */
    const val FOREVER = "forever"

    private const val EXPIRY_PATTERN = "dd-MM-yyyy"

    private val ISO_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm'Z'",
        "yyyy-MM-dd"
    )

    /**
     * Turns `dd-MM-yyyy` into the last millisecond of that day, so a bypass marked
     * as expiring today is not treated as dead at one minute past midnight.
     * Returns null for "forever", for an empty value and for anything unparseable,
     * all of which mean "do not show an expiry" rather than "expired".
     */
    fun parseExpiryDay(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals(FOREVER, ignoreCase = true)) return null
        return try {
            val format = SimpleDateFormat(EXPIRY_PATTERN, Locale.US).apply { isLenient = false }
            val parsed = format.parse(trimmed) ?: return null
            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses the `updatedAt` timestamp written by the website. Several shapes are
     * accepted because the file is also edited by hand, and an unparseable value is
     * reported as null so the UI can fall back to showing it verbatim.
     */
    fun parseIsoTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in ISO_PATTERNS) {
            val parsed = try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (pattern.endsWith("'Z'") || pattern == "yyyy-MM-dd") {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                format.parse(trimmed)
            } catch (e: Exception) {
                null
            }
            if (parsed != null) return parsed.time
        }
        return null
    }
}

/**
 * Picks the entry the user selected, falling back to the first published one when
 * that bypass disappeared from the config (or nothing was ever selected).
 */
fun List<EventBypassEntry>.selectedOrFirst(selectedId: String): EventBypassEntry? =
    firstOrNull { it.stableId() == selectedId } ?: firstOrNull()

/** Supported config versions. Anything newer is ignored rather than guessed at. */
const val EVENT_BYPASS_SUPPORTED_VERSION = 2

/**
 * Codec for the cached list of bypasses. The list is kept in settings as a JSON
 * string so the whole published set survives a restart and can be shown offline.
 */
object EventBypassCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(EventBypassEntry.serializer())

    fun encode(entries: List<EventBypassEntry>): String = json.encodeToString(serializer, entries)

    /** A corrupt cache is treated as "nothing published" rather than crashing the UI. */
    fun decode(raw: String): List<EventBypassEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
