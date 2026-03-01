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
import androidx.compose.ui.graphics.Color
import ru.protonmod.next.ui.theme.ProtonPalette
import java.util.Locale

object CountryUtils {

    /**
     * Returns a drawable resource ID for a country flag, or 0 if not found.
     */
    fun getFlagResource(context: Context, countryCode: String?): Int {
        if (countryCode == null) return 0
        // Standard mapping: UK -> GB for resource matching if necessary, 
        // but typically resources follow ISO 3166-1 alpha-2.
        val normalizedCode = when (val code = countryCode.lowercase()) {
            "uk" -> "gb"
            else -> code
        }
        val resName = "flag_$normalizedCode"
        return context.resources.getIdentifier(resName, "drawable", context.packageName)
    }

    /**
     * Generates an Emoji flag from an ISO country code (e.g., "US" -> 🇺🇸)
     */
    fun getFlagForCountry(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "🌍"
        
        val code = countryCode.uppercase()
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    /**
     * Returns the localized country name.
     */
    fun getCountryName(context: Context, countryCode: String?): String {
        if (countryCode == null) return ""
        
        val locale = Locale("", countryCode)
        val displayName = locale.getDisplayCountry(Locale.getDefault())
        
        return if (displayName.isNotEmpty() && displayName != countryCode) {
            displayName
        } else {
            val resourceName = "country_${countryCode.lowercase()}"
            val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
            if (resourceId != 0) context.getString(resourceId) else countryCode
        }
    }

    /**
     * Returns a color based on the load:
     * 0-60% -> Green (Low)
     * 60-85% -> Yellow (Medium)
     * 85-100% -> Red (High)
     */
    fun getColorForLoad(load: Int): Color {
        return when {
            load < 60 -> ProtonPalette.Apple
            load < 85 -> ProtonPalette.Sunglow
            else -> ProtonPalette.Pomegranate
        }
    }
}
