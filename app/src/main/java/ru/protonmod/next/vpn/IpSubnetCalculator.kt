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

package ru.protonmod.next.vpn

import java.net.InetAddress

/**
 * Utility class for working with IP addresses and subnets.
 * Handles conversion between IP ranges and helps calculate complements for split tunneling.
 */
object IpSubnetCalculator {

    /**
     * Check if a string is a valid IP address or CIDR notation
     */
    fun isValidIpOrCidr(input: String): Boolean {
        return try {
            when {
                input.contains("/") -> {
                    // CIDR notation: x.x.x.x/prefix
                    val parts = input.split("/")
                    if (parts.size != 2) return false
                    val ip = parts[0]
                    val prefix = parts[1].toIntOrNull() ?: return false
                    InetAddress.getByName(ip)
                    prefix in 1..32
                }
                else -> {
                    // Plain IP address
                    InetAddress.getByName(input)
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Normalize IP string to standard format (add /32 if it's a single IP)
     */
    fun normalizeIp(ip: String): String {
        return if (ip.contains("/")) ip else "$ip/32"
    }

    /**
     * Convert a list of IPs to CIDR notation for use in WireGuard
     */
    fun toWireGuardFormat(ips: Collection<String>): List<String> {
        return ips
            .filter { it.isNotBlank() }
            .map { normalizeIp(it.trim()) }
            .distinct()
    }

    // --- New helpers for CIDR/range math ---

    private fun ipToLong(ip: String): Long {
        val bytes = InetAddress.getByName(ip).address
        var result = 0L
        for (b in bytes) {
            result = (result shl 8) or (b.toInt() and 0xFF).toLong()
        }
        return result
    }

    private fun longToIp(value: Long): String {
        return listOf(
            ((value shr 24) and 0xFF).toInt(),
            ((value shr 16) and 0xFF).toInt(),
            ((value shr 8) and 0xFF).toInt(),
            (value and 0xFF).toInt()
        ).joinToString(".")
    }

    private fun cidrToRange(cidr: String): Pair<Long, Long>? {
        return try {
            val parts = cidr.split("/")
            val ip = parts[0]
            val prefix = parts[1].toInt()
            val ipLong = ipToLong(ip)
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val start = ipLong and mask
            val end = start or (mask.inv() and 0xFFFFFFFFL)
            Pair(start, end)
        } catch (_: Exception) {
            null
        }
    }

    private fun rangeToCidrs(start: Long, end: Long): List<String> {
        var s = start
        val result = mutableListOf<String>()
        while (s <= end) {
            // Max size of block starting at s
            val maxSize = s and -s // largest power of two divisor
            val maxLen = 32 - java.lang.Long.numberOfTrailingZeros(maxSize)
            // adjust maxLen to fit in the remaining range
            var pref = maxLen
            while (true) {
                val mask = if (pref == 0) 0L else (0xFFFFFFFFL shl (32 - pref)) and 0xFFFFFFFFL
                val blockSize = (mask.inv() and 0xFFFFFFFFL) + 1
                if (s + blockSize - 1 > end) {
                    pref += 1
                } else break
            }
            val cidr = "${longToIp(s)}/$pref"
            result.add(cidr)
            val mask = if (pref == 0) 0L else (0xFFFFFFFFL shl (32 - pref)) and 0xFFFFFFFFL
            val blockSize = (mask.inv() and 0xFFFFFFFFL) + 1
            s += blockSize
        }
        return result
    }

    /**
     * Subtract a set of CIDR blocks (or single IPs) from the full IPv4 space and return
     * a minimal list of CIDR blocks representing the complement (i.e. addresses that are NOT excluded).
     * If any input is invalid, it's ignored.
     */
    fun complementOfExcluded(excludedCidrs: Collection<String>): List<String> {
        // Start with full IPv4 range
        var ranges = mutableListOf<Pair<Long, Long>>(Pair(0L, 0xFFFFFFFFL))

        for (raw in excludedCidrs) {
            val cidr = normalizeIp(raw)
            val excRange = cidrToRange(cidr) ?: continue
            val newRanges = mutableListOf<Pair<Long, Long>>()
            for (r in ranges) {
                val (rs, re) = r
                val (es, ee) = excRange
                if (ee < rs || es > re) {
                    // no overlap
                    newRanges.add(r)
                } else {
                    // overlap - add left part if any
                    if (es > rs) newRanges.add(Pair(rs, es - 1))
                    // add right part if any
                    if (ee < re) newRanges.add(Pair(ee + 1, re))
                }
            }
            ranges = newRanges
            if (ranges.isEmpty()) break
        }

        // Convert remaining ranges to CIDRs
        val cidrs = mutableListOf<String>()
        for ((rs, re) in ranges) {
            cidrs.addAll(rangeToCidrs(rs, re))
        }
        return cidrs
    }
}
