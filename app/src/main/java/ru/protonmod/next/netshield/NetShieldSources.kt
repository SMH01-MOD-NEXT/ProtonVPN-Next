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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A ready-made blocklist a user can pick instead of the bundled default. */
data class NetShieldSourcePreset(
    val id: String,
    val displayName: String,
    val url: String,
    val categories: Set<NetShieldCategory>,
)

/**
 * Per-category source selection. A category uses, in order of priority, the user's own URL, the
 * chosen preset, or the built-in default.
 */
data class NetShieldSourceConfig(
    val presetIds: Map<NetShieldCategory, String> = emptyMap(),
    val customUrls: Map<NetShieldCategory, String> = emptyMap(),
) {
    fun withPreset(category: NetShieldCategory, presetId: String): NetShieldSourceConfig =
        copy(presetIds = presetIds + (category to presetId), customUrls = customUrls - category)

    fun withCustomUrl(category: NetShieldCategory, url: String): NetShieldSourceConfig =
        copy(presetIds = presetIds - category, customUrls = customUrls + (category to url.trim()))

    fun reset(category: NetShieldCategory): NetShieldSourceConfig =
        copy(presetIds = presetIds - category, customUrls = customUrls - category)
}

@Serializable
private data class NetShieldSourceConfigDto(
    val presets: Map<String, String> = emptyMap(),
    val urls: Map<String, String> = emptyMap(),
)

/** Catalogue of blocklist providers and the resolution rules for the active source of a category. */
object NetShieldSources {
    /** Categories that are downloaded. [NetShieldCategory.CUSTOM] is user-supplied instead. */
    val downloadableCategories: List<NetShieldCategory> =
        NetShieldCategory.entries.filterNot { it == NetShieldCategory.CUSTOM }

    val defaults: Map<NetShieldCategory, String> = mapOf(
        NetShieldCategory.MALWARE to "https://urlhaus.abuse.ch/downloads/hostfile/",
        NetShieldCategory.ADS to "https://adaway.org/hosts.txt",
        NetShieldCategory.TRACKERS to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/light.txt",
        NetShieldCategory.ADULT to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
    )

    private val allDownloadable = downloadableCategories.toSet()

    val presets: List<NetShieldSourcePreset> = listOf(
        NetShieldSourcePreset(
            "urlhaus", "URLhaus", "https://urlhaus.abuse.ch/downloads/hostfile/",
            setOf(NetShieldCategory.MALWARE)
        ),
        NetShieldSourcePreset(
            "hagezi-tif-medium", "HaGeZi Threat Intelligence",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/tif.medium.txt",
            setOf(NetShieldCategory.MALWARE)
        ),
        NetShieldSourcePreset(
            "adaway", "AdAway", "https://adaway.org/hosts.txt",
            setOf(NetShieldCategory.ADS)
        ),
        NetShieldSourcePreset(
            "adguard-dns", "AdGuard DNS filter",
            "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
            setOf(NetShieldCategory.ADS, NetShieldCategory.TRACKERS)
        ),
        NetShieldSourcePreset(
            "easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt",
            setOf(NetShieldCategory.TRACKERS)
        ),
        NetShieldSourcePreset(
            "hagezi-light", "HaGeZi Light",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/light.txt",
            allDownloadable
        ),
        NetShieldSourcePreset(
            "hagezi-pro", "HaGeZi Pro",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt",
            allDownloadable
        ),
        NetShieldSourcePreset(
            "oisd-small", "OISD Small", "https://small.oisd.nl/",
            allDownloadable
        ),
        NetShieldSourcePreset(
            "oisd-big", "OISD Big", "https://big.oisd.nl/",
            allDownloadable
        ),
        NetShieldSourcePreset(
            "stevenblack", "StevenBlack Unified",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            allDownloadable
        ),
        NetShieldSourcePreset(
            "stevenblack-porn", "StevenBlack Adult",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
            setOf(NetShieldCategory.ADULT)
        ),
        NetShieldSourcePreset(
            "hagezi-nsfw", "HaGeZi NSFW",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/nsfw.txt",
            setOf(NetShieldCategory.ADULT)
        ),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun presetsFor(category: NetShieldCategory): List<NetShieldSourcePreset> =
        presets.filter { category in it.categories }

    /** Presets that can replace every downloadable category in one tap. */
    fun universalPresets(): List<NetShieldSourcePreset> =
        presets.filter { it.categories.containsAll(allDownloadable) }

    fun preset(id: String?): NetShieldSourcePreset? = presets.firstOrNull { it.id == id }

    /** The URL actually downloaded for a category. */
    fun resolve(category: NetShieldCategory, config: NetShieldSourceConfig): String {
        config.customUrls[category]?.takeIf(String::isNotBlank)?.let { return it.trim() }
        preset(config.presetIds[category])?.takeIf { category in it.categories }?.let { return it.url }
        return defaults.getValue(category)
    }

    fun applyToAll(config: NetShieldSourceConfig, presetId: String): NetShieldSourceConfig {
        val preset = preset(presetId) ?: return config
        return downloadableCategories
            .filter { it in preset.categories }
            .fold(config) { acc, category -> acc.withPreset(category, presetId) }
    }

    fun isValidUrl(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) &&
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/').contains('.')
    }

    fun decode(raw: String): NetShieldSourceConfig {
        if (raw.isBlank()) return NetShieldSourceConfig()
        val dto = runCatching { json.decodeFromString<NetShieldSourceConfigDto>(raw) }.getOrNull()
            ?: return NetShieldSourceConfig()
        return NetShieldSourceConfig(
            presetIds = dto.presets.mapNotNull { (key, value) -> category(key)?.let { it to value } }.toMap(),
            customUrls = dto.urls.mapNotNull { (key, value) -> category(key)?.let { it to value } }.toMap(),
        )
    }

    fun encode(config: NetShieldSourceConfig): String = json.encodeToString(
        NetShieldSourceConfigDto(
            presets = config.presetIds.mapKeys { (category, _) -> category.name },
            urls = config.customUrls.mapKeys { (category, _) -> category.name },
        )
    )

    /** Identifies the effective source set, so downloaded lists are refetched when it changes. */
    fun fingerprint(config: NetShieldSourceConfig): String = downloadableCategories
        .joinToString("|") { category -> "${category.name}=${resolve(category, config)}" }

    private fun category(name: String): NetShieldCategory? =
        NetShieldCategory.entries.firstOrNull { it.name == name }
}
