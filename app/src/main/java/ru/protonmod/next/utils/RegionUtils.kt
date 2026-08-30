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

package ru.protonmod.next.utils

import java.util.Locale
import java.util.TimeZone

object RegionUtils {

    private val russianTimezones = setOf(
        "Europe/Kaliningrad",
        "Europe/Moscow",
        "Europe/Simferopol",
        "Europe/Kirov",
        "Europe/Astrakhan",
        "Europe/Volgograd",
        "Europe/Saratov",
        "Europe/Ulyanovsk",
        "Europe/Samara",
        "Asia/Yekaterinburg",
        "Asia/Omsk",
        "Asia/Novosibirsk",
        "Asia/Barnaul",
        "Asia/Tomsk",
        "Asia/Novokuznetsk",
        "Asia/Krasnoyarsk",
        "Asia/Irkutsk",
        "Asia/Chita",
        "Asia/Yakutsk",
        "Asia/Khandyga",
        "Asia/Vladivostok",
        "Asia/Ust-Nera",
        "Asia/Magadan",
        "Asia/Sakhalin",
        "Asia/Srednekolymsk",
        "Asia/Kamchatka",
        "Asia/Anadyr"
    )

    fun isRussianTimezone(): Boolean {
        val defaultTz = TimeZone.getDefault().id
        return russianTimezones.contains(defaultTz)
    }

    /**
     * Whether the device appears to be used inside Russia.
     *
     * Decided from device settings alone. The obvious alternative, asking a
     * geolocation service, would mean disclosing the user's address to a third
     * party in order to decide how to protect that user's DNS queries, which
     * defeats the purpose. Timezone and configured region are both local and
     * free.
     *
     * The two signals are OR-ed rather than AND-ed because they fail in
     * different directions: a traveller keeps their region while changing
     * timezone, and a user who sets an English locale keeps their timezone.
     *
     * The asymmetry is intentional. A false positive only leaves DNS over TLS
     * available as a fallback, which is never harmful. A false negative gives
     * up a defence, so this errs towards over-detection.
     */
    fun isRussianRegion(): Boolean =
        isRussianTimezone() || Locale.getDefault().country.equals("RU", ignoreCase = true)
}
