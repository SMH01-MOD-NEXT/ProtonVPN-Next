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

package ru.protonmod.next.data.model

import ru.protonmod.next.data.local.SettingsManager

data class ObfuscationProfile(
    val id: String,
    val name: String,
    val isReadOnly: Boolean,
    val jc: Int,
    val jmin: Int,
    val jmax: Int,
    val s1: Int,
    val s2: Int,
    val h1: String,
    val h2: String,
    val h3: String,
    val h4: String,
    val i1: String
) {
    companion object {
        fun getStandardProfile(name: String = "Standard") = ObfuscationProfile(
            id = "standard_1",
            name = name,
            isReadOnly = true,
            jc = 3, jmin = 1, jmax = 3,
            s1 = 0, s2 = 0,
            h1 = "1", h2 = "2", h3 = "3", h4 = "4",
            i1 = SettingsManager.DEFAULT_I1
        )

        fun createDefaultCustomProfile(id: String, name: String) = ObfuscationProfile(
            id = id,
            name = name,
            isReadOnly = false,
            jc = 3, jmin = 1, jmax = 3,
            s1 = 0, s2 = 0,
            h1 = "1", h2 = "2", h3 = "3", h4 = "4",
            i1 = SettingsManager.DEFAULT_I1
        )
    }
}
