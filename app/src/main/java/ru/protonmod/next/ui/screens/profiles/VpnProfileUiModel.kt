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

package ru.protonmod.next.ui.screens.profiles

import java.util.UUID

data class VpnProfileUiModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val protocol: String = "AmneziaWG", // Default protocol
    val port: Int = 0,                 // 0 = Auto
    val isObfuscationEnabled: Boolean = false,
    val obfuscationProfileId: String? = null,
    val autoOpenUrl: String? = null,
    val targetServerId: String? = null,
    val targetCountry: String? = null,
    val targetCity: String? = null
)
