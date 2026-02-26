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
import java.util.Locale

object CountryUtils {

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
            load < 60 -> Color(0xFF4CAF50) // Green
            load < 85 -> Color(0xFFFFC107) // Amber/Yellow
            else -> Color(0xFFF44336)      // Red
        }
    }
}
