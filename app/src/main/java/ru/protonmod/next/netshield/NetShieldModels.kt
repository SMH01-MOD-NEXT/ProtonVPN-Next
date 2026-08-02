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

package ru.protonmod.next.netshield

enum class NetShieldLevel {
    DISABLED,
    MALWARE,
    ADS_TRACKERS,
    ADS_TRACKERS_ADULT;

    val enabled: Boolean get() = this != DISABLED
}

/** CUSTOM holds the user's own blocklist; it is never downloaded and is always active. */
enum class NetShieldCategory { MALWARE, ADS, TRACKERS, ADULT, CUSTOM }

data class NetShieldStats(
    val malwareBlocked: Long = 0,
    val adsBlocked: Long = 0,
    val trackersBlocked: Long = 0,
    val savedBytes: Long = 0,
)

data class NetShieldRuleSet(
    val tag: String,
    val path: String,
    val category: NetShieldCategory,
)

data class NetShieldListState(
    val isUpdating: Boolean = false,
    val lastUpdatedAt: Long = 0,
    val domainCount: Int = 0,
    val customDomainCount: Int = 0,
    val isImporting: Boolean = false,
    val importedCount: Int? = null,
    val error: String? = null,
)
