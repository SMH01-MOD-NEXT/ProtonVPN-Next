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

package ru.protonmod.next.data.local

/** Controls active tunnel probes and transport-failure sensitivity. */
enum class ConnectionVerificationMode(
    val verificationTimeoutMs: Long,
    val verificationRetryDelayMs: Long,
    val failureThreshold: Int,
    val failureWindowMs: Long,
    val reconnectCooldownMs: Long,
) {
    DISABLED(0, 0, Int.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE),
    RELAXED(5_000, 0, 4, 30_000, 45_000),
    BALANCED(8_000, 200, 2, 15_000, 15_000),
    AGGRESSIVE(5_000, 100, 1, 8_000, 5_000);

    val handshakeOnly: Boolean
        get() = this == RELAXED
}
