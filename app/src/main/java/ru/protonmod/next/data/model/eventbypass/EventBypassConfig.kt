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

/**
 * Shape of `event-bypass.json`, published by the website and mirrored on every
 * host we deploy to. It describes the one temporary ("event") proxy that is
 * currently available, so the platform behind the Event strategy can change
 * without shipping a new APK.
 */
@Serializable
data class EventBypassResponse(
    val version: Int = 1,
    val updatedAt: String = "",
    val event: EventBypassEntry? = null
)

@Serializable
data class EventBypassEntry(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = false
) {
    /**
     * Returns the URL the interceptor can use as a base URL, or an empty string
     * when nothing usable is published. Plaintext endpoints are refused: the
     * whole point of the bypass is to carry API traffic, and the certificate
     * checks downstream assume HTTPS.
     */
    fun normalizedUrl(): String {
        if (!enabled) return ""
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}

/** Supported config versions. Anything newer is ignored rather than guessed at. */
const val EVENT_BYPASS_SUPPORTED_VERSION = 1
