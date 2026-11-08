/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

/** Decodes hexadecimal test vectors independently of the production decoder. */
internal fun hexBytes(value: String): ByteArray {
    require(value.length % 2 == 0) { "Odd hex length" }
    return ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

/** Lexicographic order of unsigned bytes, the order WireGuard applies to TAI64N labels. */
internal fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
    for (index in 0 until minOf(first.size, second.size)) {
        val difference = (first[index].toInt() and 0xFF) - (second[index].toInt() and 0xFF)
        if (difference != 0) return difference
    }
    return first.size - second.size
}

/** Unsigned little-endian 32-bit value, as WireGuard encodes message types and indices. */
internal fun ByteArray.uint32Le(offset: Int): Long =
    (0 until 4).fold(0L) { value, index -> value or ((this[offset + index].toLong() and 0xFF) shl (8 * index)) }
