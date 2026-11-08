/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

enum class ServerScanPortMode { CONNECTION, ALL_STANDARD, CUSTOM }

enum class ServerScanObfuscationMode { CONNECTION, ALWAYS, NEVER }

data class ServerScanPreferences(
    val threads: Int = DEFAULT_THREADS,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val attempts: Int = DEFAULT_ATTEMPTS,
    val portMode: ServerScanPortMode = ServerScanPortMode.CONNECTION,
    val customPort: Int = DEFAULT_CUSTOM_PORT,
    val obfuscationMode: ServerScanObfuscationMode = ServerScanObfuscationMode.CONNECTION,
    val tcpCheck: Boolean = true,
    val dataCheck: Boolean = false,
) {
    fun normalized(): ServerScanPreferences = copy(
        threads = threads.coerceIn(THREADS_RANGE),
        timeoutMs = timeoutMs.coerceIn(TIMEOUT_RANGE_MS),
        attempts = attempts.coerceIn(ATTEMPTS_RANGE),
        customPort = customPort.coerceIn(PORT_RANGE),
    )

    companion object {
        const val DEFAULT_THREADS = 64
        const val DEFAULT_TIMEOUT_MS = 1_200
        const val DEFAULT_ATTEMPTS = 2
        const val DEFAULT_CUSTOM_PORT = 443

        val THREADS_RANGE = 1..256
        val TIMEOUT_RANGE_MS = 300..5_000
        val ATTEMPTS_RANGE = 1..5
        val PORT_RANGE = 1..65_535

        /** Ports of Proton WireGuard servers; the automatic port of the connection settings is one of them. */
        val STANDARD_PORTS = listOf(443, 123, 1194, 51820)
    }
}
