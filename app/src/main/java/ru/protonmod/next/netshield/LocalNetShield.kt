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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.di.ApplicationScope
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNetShield @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsManager: SettingsManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private data class Source(val category: NetShieldCategory, val url: String, val fileName: String)

    private val directory = File(context.filesDir, "netshield").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("netshield", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val updateMutex = Mutex()
    private val customMutex = Mutex()
    private val statsFlushMutex = Mutex()
    private val pendingMalware = AtomicLong(0)
    private val pendingAds = AtomicLong(0)
    private val pendingTrackers = AtomicLong(0)
    private val pendingSavedBytes = AtomicLong(0)
    private var statsPublishJob: Job? = null

    @Volatile private var domains = loadDomains()
    val stats: StateFlow<NetShieldStats> = settingsManager.netShieldStats.stateIn(
        applicationScope,
        SharingStarted.Eagerly,
        NetShieldStats(),
    )

    /** The per-category list providers the user selected. */
    val sourceConfig: StateFlow<NetShieldSourceConfig> = settingsManager.netShieldSources
        .map(NetShieldSources::decode)
        .stateIn(applicationScope, SharingStarted.Eagerly, NetShieldSourceConfig())

    private val _listState = MutableStateFlow(
        NetShieldListState(
            lastUpdatedAt = preferences.getLong(KEY_UPDATED_AT, 0),
            domainCount = preferences.getInt(KEY_DOMAIN_COUNT, 0),
            customDomainCount = domains[NetShieldCategory.CUSTOM]?.size ?: 0,
        )
    )
    val listState: StateFlow<NetShieldListState> = _listState.asStateFlow()

    /** True when the bundled lists are stale, or when the user changed the list providers. */
    suspend fun needsListUpdate(): Boolean {
        if (preferences.getInt(KEY_SOURCE_VERSION, 0) < SOURCE_VERSION) return true
        return preferences.getString(KEY_SOURCE_FINGERPRINT, null) != NetShieldSources.fingerprint(currentConfig())
    }

    fun beginSessionStats() {
        pendingMalware.set(0)
        pendingAds.set(0)
        pendingTrackers.set(0)
        pendingSavedBytes.set(0)
        statsPublishJob?.cancel()
        statsPublishJob = applicationScope.launch {
            settingsManager.resetNetShieldStats()
            while (true) {
                delay(STATS_PUBLISH_INTERVAL_MS)
                flushPendingStats()
            }
        }
    }

    suspend fun finishSessionStats() {
        statsPublishJob?.cancel()
        statsPublishJob = null
        flushPendingStats()
    }

    fun activeRuleSets(level: NetShieldLevel): List<NetShieldRuleSet> {
        val categories = when (level) {
            NetShieldLevel.DISABLED -> emptySet()
            NetShieldLevel.MALWARE -> setOf(NetShieldCategory.MALWARE)
            NetShieldLevel.ADS_TRACKERS -> setOf(NetShieldCategory.MALWARE, NetShieldCategory.ADS, NetShieldCategory.TRACKERS)
            NetShieldLevel.ADS_TRACKERS_ADULT -> NetShieldSources.downloadableCategories.toSet()
        }
        // The user's own list applies at every protection level as long as NetShield is on.
        val effective = if (level.enabled) categories + NetShieldCategory.CUSTOM else categories
        return effective.mapNotNull { category ->
            File(directory, fileName(category) + ".json").takeIf(File::isFile)?.let {
                NetShieldRuleSet("netshield-${category.name.lowercase(Locale.ROOT)}", it.absolutePath, category)
            }
        }
    }

    suspend fun updateLists(): Result<Int> = updateMutex.withLock {
        _listState.update { it.copy(isUpdating = true, error = null) }
        val sources = currentSources()
        withContext(Dispatchers.IO) {
            runCatching {
                // Download and validate every source before replacing any active file.
                val updated = sources.associate { source ->
                    val parsed = NetShieldDomainParser.parse(download(source.url))
                        .filterNot { domain -> source.category in COMPATIBILITY_FILTERED_CATEGORIES && blocksProtectedDomain(domain) }
                        .toSet()
                    check(parsed.isNotEmpty()) { "${source.category}: empty rule list" }
                    source.category to parsed
                }.toMutableMap()

                // Ad lists often include a small number of analytics entries. Keep category
                // counters deterministic by assigning overlaps to ads and only the remainder to trackers.
                updated[NetShieldCategory.TRACKERS] = updated.getValue(NetShieldCategory.TRACKERS) -
                    updated.getValue(NetShieldCategory.ADS)

                sources.forEach { source -> writeRuleSet(source.fileName, updated.getValue(source.category)) }
                val custom = domains[NetShieldCategory.CUSTOM].orEmpty()
                domains = updated + (NetShieldCategory.CUSTOM to custom)
                val count = updated.values.sumOf(Set<String>::size)
                val now = System.currentTimeMillis()
                preferences.edit()
                    .putLong(KEY_UPDATED_AT, now)
                    .putInt(KEY_DOMAIN_COUNT, count)
                    .putInt(KEY_SOURCE_VERSION, SOURCE_VERSION)
                    .putString(KEY_SOURCE_FINGERPRINT, NetShieldSources.fingerprint(currentConfig()))
                    .apply()
                _listState.update {
                    NetShieldListState(lastUpdatedAt = now, domainCount = count, customDomainCount = custom.size)
                }
                count
            }.onFailure { error ->
                _listState.update { it.copy(isUpdating = false, error = error.message ?: error.javaClass.simpleName) }
            }
        }
    }

    /**
     * Adds the user's own rules, accepting hosts files, adblock syntax or a plain domain list.
     *
     * @param content raw text pasted by the user or read from a file.
     * @param replace true to overwrite the existing list instead of merging into it.
     * @return the number of rules stored, or a failure when nothing could be parsed.
     */
    suspend fun importCustomFilters(content: String, replace: Boolean = false): Result<Int> =
        customMutex.withLock {
            _listState.update { it.copy(isImporting = true, error = null, importedCount = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = NetShieldDomainParser.parse(content)
                    check(parsed.isNotEmpty()) { "no valid domains found" }
                    val merged = if (replace) parsed else domains[NetShieldCategory.CUSTOM].orEmpty() + parsed
                    storeCustomFilters(merged)
                    _listState.update {
                        it.copy(isImporting = false, customDomainCount = merged.size, importedCount = parsed.size)
                    }
                    parsed.size
                }.onFailure { error ->
                    _listState.update {
                        it.copy(isImporting = false, error = error.message ?: error.javaClass.simpleName)
                    }
                }
            }
        }

    /** Downloads a filter list chosen by the user and merges it into their own rules. */
    suspend fun importCustomFiltersFromUrl(url: String, replace: Boolean = false): Result<Int> {
        if (!NetShieldSources.isValidUrl(url)) {
            _listState.update { it.copy(error = "invalid URL") }
            return Result.failure(IllegalArgumentException("invalid URL"))
        }
        _listState.update { it.copy(isImporting = true, error = null, importedCount = null) }
        val content = withContext(Dispatchers.IO) { runCatching { download(url.trim()) } }
        return content.fold(
            onSuccess = { importCustomFilters(it, replace) },
            onFailure = { error ->
                _listState.update {
                    it.copy(isImporting = false, error = error.message ?: error.javaClass.simpleName)
                }
                Result.failure(error)
            }
        )
    }

    suspend fun clearCustomFilters() = customMutex.withLock {
        withContext(Dispatchers.IO) {
            storeCustomFilters(emptySet())
            _listState.update { it.copy(customDomainCount = 0, importedCount = null, error = null) }
        }
    }

    /** Replaces the provider of a single category with a preset. */
    suspend fun setCategoryPreset(category: NetShieldCategory, presetId: String) =
        updateSourceConfig { it.withPreset(category, presetId) }

    /** Replaces the provider of a single category with a user-supplied URL. */
    suspend fun setCategoryUrl(category: NetShieldCategory, url: String) =
        updateSourceConfig { it.withCustomUrl(category, url) }

    suspend fun resetCategorySource(category: NetShieldCategory) =
        updateSourceConfig { it.reset(category) }

    /** Uses one preset for every downloadable category it supports. */
    suspend fun applyPresetToAll(presetId: String) =
        updateSourceConfig { NetShieldSources.applyToAll(it, presetId) }

    suspend fun resetAllSources() = updateSourceConfig { NetShieldSourceConfig() }

    private suspend fun updateSourceConfig(transform: (NetShieldSourceConfig) -> NetShieldSourceConfig) {
        val updated = transform(currentConfig())
        settingsManager.setNetShieldSources(NetShieldSources.encode(updated))
        // Stored lists belong to the previous providers, so force a refetch on the next check.
        preferences.edit().remove(KEY_SOURCE_FINGERPRINT).apply()
    }

    fun recordEngineLog(message: String) {
        val directCategory = categoryFromRuleSetLog(message)
        val category = directCategory ?: run {
            val match = REJECTED_DNS.find(message) ?: return
            val host = match.groupValues[1].trimEnd('.').lowercase(Locale.ROOT)
            classify(host)
        } ?: return
        when (category) {
            NetShieldCategory.MALWARE -> pendingMalware.incrementAndGet()
            NetShieldCategory.ADS -> {
                pendingAds.incrementAndGet()
                pendingSavedBytes.addAndGet(ESTIMATED_AD_BYTES)
            }
            NetShieldCategory.TRACKERS -> {
                pendingTrackers.incrementAndGet()
                pendingSavedBytes.addAndGet(ESTIMATED_TRACKER_BYTES)
            }
            // Adult and user-defined blocks are intentionally not part of the shown counters.
            NetShieldCategory.ADULT, NetShieldCategory.CUSTOM -> Unit
        }
    }

    private suspend fun flushPendingStats() = statsFlushMutex.withLock {
        val malware = pendingMalware.getAndSet(0)
        val ads = pendingAds.getAndSet(0)
        val trackers = pendingTrackers.getAndSet(0)
        val savedBytes = pendingSavedBytes.getAndSet(0)
        runCatching { settingsManager.addNetShieldStats(malware, ads, trackers, savedBytes) }
            .onFailure {
                // Restore deltas so a transient multi-process DataStore failure cannot lose counts.
                pendingMalware.addAndGet(malware)
                pendingAds.addAndGet(ads)
                pendingTrackers.addAndGet(trackers)
                pendingSavedBytes.addAndGet(savedBytes)
            }
    }

    private fun classify(host: String): NetShieldCategory? {
        val snapshot = domains
        val order = listOf(
            NetShieldCategory.CUSTOM,
            NetShieldCategory.TRACKERS,
            NetShieldCategory.ADS,
            NetShieldCategory.MALWARE,
            NetShieldCategory.ADULT,
        )
        val suffixes = generateSequence(host) { value -> value.substringAfter('.', "").takeIf(String::isNotEmpty) }.toList()
        return order.firstOrNull { category -> suffixes.any(snapshot[category].orEmpty()::contains) }
    }

    private suspend fun currentConfig(): NetShieldSourceConfig =
        NetShieldSources.decode(settingsManager.netShieldSources.first())

    private suspend fun currentSources(): List<Source> {
        val config = currentConfig()
        return NetShieldSources.downloadableCategories.map { category ->
            Source(category, NetShieldSources.resolve(category, config), fileName(category))
        }
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "ProtonVPN-Next/NetShield").build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body.string()
        }
    }

    private suspend fun storeCustomFilters(values: Set<String>) {
        writeRuleSet(fileName(NetShieldCategory.CUSTOM), values)
        domains = domains + (NetShieldCategory.CUSTOM to values)
        settingsManager.setNetShieldCustomDomains(values.sorted().joinToString("\n"))
    }

    private fun loadDomains(): Map<NetShieldCategory, Set<String>> = NetShieldCategory.entries.associateWith { category ->
        File(directory, fileName(category) + ".domains")
            .takeIf(File::isFile)?.readLines()?.filter(String::isNotBlank)?.toSet().orEmpty()
    }

    private fun writeRuleSet(fileName: String, values: Set<String>) {
        val sorted = values.sorted()
        val json = JsonObject(mapOf(
            "version" to JsonPrimitive(3),
            "rules" to JsonArray(listOf(JsonObject(mapOf(
                "domain_suffix" to JsonArray(sorted.map(::JsonPrimitive))
            ))))
        ))
        val jsonFile = File(directory, "$fileName.json")
        val domainFile = File(directory, "$fileName.domains")
        val jsonTmp = File(directory, "$fileName.json.tmp")
        val domainTmp = File(directory, "$fileName.domains.tmp")
        jsonTmp.writeText(Json.encodeToString(JsonObject.serializer(), json))
        domainTmp.writeText(sorted.joinToString("\n"))
        check(jsonTmp.renameTo(jsonFile) || jsonTmp.copyTo(jsonFile, overwrite = true).let { jsonTmp.delete(); true })
        check(domainTmp.renameTo(domainFile) || domainTmp.copyTo(domainFile, overwrite = true).let { domainTmp.delete(); true })
    }

    private fun fileName(category: NetShieldCategory): String = category.name.lowercase(Locale.ROOT)

    internal companion object {
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_DOMAIN_COUNT = "domain_count"
        const val KEY_SOURCE_VERSION = "source_version"
        const val KEY_SOURCE_FINGERPRINT = "source_fingerprint"
        const val SOURCE_VERSION = 3
        const val STATS_PUBLISH_INTERVAL_MS = 2_000L
        const val ESTIMATED_AD_BYTES = 150_000L
        const val ESTIMATED_TRACKER_BYTES = 4_000L
        val RULE_SET_REJECT = Regex("rule_set=netshield-(malware|ads|trackers|adult|custom)\\b.*=>\\s*reject", RegexOption.IGNORE_CASE)
        val REJECTED_DNS = Regex("rejected\\s+(?:A|AAAA|HTTPS|SVCB)\\s+([^\\s]+)", RegexOption.IGNORE_CASE)
        val COMPATIBILITY_FILTERED_CATEGORIES = setOf(NetShieldCategory.ADS, NetShieldCategory.TRACKERS)
        internal fun categoryFromRuleSetLog(message: String): NetShieldCategory? =
            RULE_SET_REJECT.find(message)?.groupValues?.get(1)?.let { value ->
                when (value.lowercase(Locale.ROOT)) {
                    "malware" -> NetShieldCategory.MALWARE
                    "ads" -> NetShieldCategory.ADS
                    "trackers" -> NetShieldCategory.TRACKERS
                    "adult" -> NetShieldCategory.ADULT
                    "custom" -> NetShieldCategory.CUSTOM
                    else -> null
                }
            }

        internal fun blocksProtectedDomain(blockedDomain: String): Boolean = PROTECTED_SERVICE_DOMAINS.any { protected ->
            protected == blockedDomain || protected.endsWith(".$blockedDomain")
        }

        val PROTECTED_SERVICE_DOMAINS = setOf(
            "accounts.google.com",
            "android.clients.google.com",
            "clients3.google.com",
            "firebase.googleapis.com",
            "firebaseinstallations.googleapis.com",
            "gemini.google.com",
            "generativelanguage.googleapis.com",
            "googleapis.com",
            "googleusercontent.com",
            "gstatic.com",
            "gvt1.com",
            "gvt2.com",
            "mtalk.google.com",
            "oauth2.googleapis.com",
            "play-fe.googleapis.com",
            "play.googleapis.com",
        )
    }
}
