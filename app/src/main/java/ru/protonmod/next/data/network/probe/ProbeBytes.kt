/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

internal fun ByteArray.readIntLe(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.writeIntLe(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}

internal fun ByteArray.readLongLe(offset: Int): Long {
    var result = 0L
    for (index in 7 downTo 0) {
        result = (result shl 8) or (this[offset + index].toLong() and 0xFF)
    }
    return result
}

internal fun ByteArray.writeLongLe(offset: Int, value: Long) {
    for (index in 0 until 8) {
        this[offset + index] = (value ushr (8 * index)).toByte()
    }
}

internal fun ByteArray.writeIntBe(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

internal fun ByteArray.writeLongBe(offset: Int, value: Long) {
    for (index in 0 until 8) {
        this[offset + index] = (value ushr (8 * (7 - index))).toByte()
    }
}

internal fun ByteArray.readShortBe(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

internal fun ByteArray.writeShortBe(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

/** Decodes an even-length hexadecimal string; returns null for malformed input. */
internal fun decodeHex(value: String): ByteArray? {
    if (value.length % 2 != 0) return null
    val result = ByteArray(value.length / 2)
    for (index in result.indices) {
        val high = Character.digit(value[index * 2], 16)
        val low = Character.digit(value[index * 2 + 1], 16)
        if (high < 0 || low < 0) return null
        result[index] = ((high shl 4) or low).toByte()
    }
    return result
}
