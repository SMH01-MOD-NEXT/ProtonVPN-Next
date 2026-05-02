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

package ru.protonmod.next.data.network

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DohFallbackStore @Inject constructor() {
    private val fallbackIps = ConcurrentHashMap<String, List<InetAddress>>()

    fun setFallbackIps(hostname: String, ips: List<String>) {
        val inetAddresses = ips.mapNotNull {
            try {
                InetAddress.getByName(it)
            } catch (e: Exception) {
                null
            }
        }
        if (inetAddresses.isNotEmpty()) {
            // Prefer IPv4 over IPv6 so that callers attempt reachable addresses first
            // on networks without IPv6 connectivity.
            fallbackIps[hostname] = inetAddresses.sortedWith(compareBy { if (it is Inet4Address) 0 else 1 })
        }
    }

    fun getFallbackIps(hostname: String): List<InetAddress>? {
        return fallbackIps[hostname]
    }

    fun clearFallbackIps(hostname: String) {
        fallbackIps.remove(hostname)
    }
}
