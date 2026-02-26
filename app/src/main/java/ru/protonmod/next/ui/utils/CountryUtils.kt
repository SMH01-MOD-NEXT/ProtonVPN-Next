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

package ru.protonmod.next.ui.utils

import android.content.Context

object CountryUtils {
    // Map of country codes to emoji flags
    private val countryFlags = mapOf(
        "AR" to "🇦🇷", "AU" to "🇦🇺", "AT" to "🇦🇹", "BE" to "🇧🇪", "BR" to "🇧🇷",
        "CA" to "🇨🇦", "CH" to "🇨🇭", "CL" to "🇨🇱", "CZ" to "🇨🇿", "DE" to "🇩🇪",
        "DK" to "🇩🇰", "ES" to "🇪🇸", "FI" to "🇫🇮", "FR" to "🇫🇷", "GB" to "🇬🇧",
        "GR" to "🇬🇷", "HU" to "🇭🇺", "IE" to "🇮🇪", "IL" to "🇮🇱", "IT" to "🇮🇹",
        "JP" to "🇯🇵", "KR" to "🇰🇷", "MX" to "🇲🇽", "NL" to "🇳🇱", "NZ" to "🇳🇿",
        "NO" to "🇳🇴", "PL" to "🇵🇱", "PT" to "🇵🇹", "RO" to "🇷🇴", "RU" to "🇷🇺",
        "SE" to "🇸🇪", "SG" to "🇸🇬", "SK" to "🇸🇰", "TR" to "🇹🇷", "UA" to "🇺🇦",
        "US" to "🇺🇸", "VN" to "🇻🇳", "ZA" to "🇿🇦"
    )

    fun getFlagForCountry(countryCode: String): String {
        return countryFlags[countryCode.uppercase()] ?: "🌍"
    }

    fun getCountryName(context: Context, countryCode: String): String {
        val resourceName = "country_${countryCode.lowercase()}"
        val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)

        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            countryCode
        }
    }
}



