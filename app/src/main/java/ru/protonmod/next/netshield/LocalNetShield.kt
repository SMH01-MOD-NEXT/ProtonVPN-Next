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
    private val _listState = MutableStateFlow(
        NetShieldListState(
            lastUpdatedAt = preferences.getLong(KEY_UPDATED_AT, 0),
            domainCount = preferences.getInt(KEY_DOMAIN_COUNT, 0),
        )
    )
    val listState: StateFlow<NetShieldListState> = _listState.asStateFlow()
    val needsListUpdate: Boolean
        get() = preferences.getInt(KEY_SOURCE_VERSION, 0) < SOURCE_VERSION

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
            NetShieldLevel.ADS_TRACKERS_ADULT -> NetShieldCategory.entries.toSet()
        }
        return categories.mapNotNull { category ->
            val source = SOURCES.first { it.category == category }
            File(directory, source.fileName + ".json").takeIf(File::isFile)?.let {
                NetShieldRuleSet("netshield-${category.name.lowercase(Locale.ROOT)}", it.absolutePath, category)
            }
        }
    }

    suspend fun updateLists(): Result<Int> = updateMutex.withLock {
        _listState.update { it.copy(isUpdating = true, error = null) }
        withContext(Dispatchers.IO) {
            runCatching {
                // Download and validate every source before replacing any active file.
                val updated = SOURCES.associate { source ->
                    val request = Request.Builder().url(source.url).header("User-Agent", "ProtonVPN-Next/NetShield").build()
                    val body = client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "${source.category}: HTTP ${response.code}" }
                        response.body.string()
                    }
                    val parsed = NetShieldDomainParser.parse(body)
                        .filterNot { domain -> source.category in COMPATIBILITY_FILTERED_CATEGORIES && blocksProtectedDomain(domain) }
                        .toSet()
                    check(parsed.isNotEmpty()) { "${source.category}: empty rule list" }
                    source.category to parsed
                }.toMutableMap()

                // AdAway already includes a small number of analytics entries. Keep category
                // counters deterministic by assigning overlaps to ads and only the remainder to trackers.
                updated[NetShieldCategory.TRACKERS] = updated.getValue(NetShieldCategory.TRACKERS) -
                    updated.getValue(NetShieldCategory.ADS)

                SOURCES.forEach { source -> writeRuleSet(source, updated.getValue(source.category)) }
                domains = updated
                val count = updated.values.sumOf(Set<String>::size)
                val now = System.currentTimeMillis()
                preferences.edit()
                    .putLong(KEY_UPDATED_AT, now)
                    .putInt(KEY_DOMAIN_COUNT, count)
                    .putInt(KEY_SOURCE_VERSION, SOURCE_VERSION)
                    .apply()
                _listState.value = NetShieldListState(lastUpdatedAt = now, domainCount = count)
                count
            }.onFailure { error ->
                _listState.update { it.copy(isUpdating = false, error = error.message ?: error.javaClass.simpleName) }
            }
        }
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
            NetShieldCategory.ADULT -> Unit
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
        val order = listOf(NetShieldCategory.TRACKERS, NetShieldCategory.ADS, NetShieldCategory.MALWARE, NetShieldCategory.ADULT)
        val suffixes = generateSequence(host) { value -> value.substringAfter('.', "").takeIf(String::isNotEmpty) }.toList()
        return order.firstOrNull { category -> suffixes.any(snapshot[category].orEmpty()::contains) }
    }

    private fun loadDomains(): Map<NetShieldCategory, Set<String>> = SOURCES.associate { source ->
        source.category to File(directory, source.fileName + ".domains")
            .takeIf(File::isFile)?.readLines()?.filter(String::isNotBlank)?.toSet().orEmpty()
    }

    private fun writeRuleSet(source: Source, values: Set<String>) {
        val sorted = values.sorted()
        val json = JsonObject(mapOf(
            "version" to JsonPrimitive(3),
            "rules" to JsonArray(listOf(JsonObject(mapOf(
                "domain_suffix" to JsonArray(sorted.map(::JsonPrimitive))
            ))))
        ))
        val jsonFile = File(directory, source.fileName + ".json")
        val domainFile = File(directory, source.fileName + ".domains")
        val jsonTmp = File(directory, source.fileName + ".json.tmp")
        val domainTmp = File(directory, source.fileName + ".domains.tmp")
        jsonTmp.writeText(Json.encodeToString(JsonObject.serializer(), json))
        domainTmp.writeText(sorted.joinToString("\n"))
        check(jsonTmp.renameTo(jsonFile) || jsonTmp.copyTo(jsonFile, overwrite = true).let { jsonTmp.delete(); true })
        check(domainTmp.renameTo(domainFile) || domainTmp.copyTo(domainFile, overwrite = true).let { domainTmp.delete(); true })
    }

    internal companion object {
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_DOMAIN_COUNT = "domain_count"
        const val KEY_SOURCE_VERSION = "source_version"
        const val SOURCE_VERSION = 2
        const val STATS_PUBLISH_INTERVAL_MS = 2_000L
        const val ESTIMATED_AD_BYTES = 150_000L
        const val ESTIMATED_TRACKER_BYTES = 4_000L
        val RULE_SET_REJECT = Regex("rule_set=netshield-(malware|ads|trackers|adult)\\b.*=>\\s*reject", RegexOption.IGNORE_CASE)
        val REJECTED_DNS = Regex("rejected\\s+(?:A|AAAA|HTTPS|SVCB)\\s+([^\\s]+)", RegexOption.IGNORE_CASE)
        val COMPATIBILITY_FILTERED_CATEGORIES = setOf(NetShieldCategory.ADS, NetShieldCategory.TRACKERS)
        internal fun categoryFromRuleSetLog(message: String): NetShieldCategory? =
            RULE_SET_REJECT.find(message)?.groupValues?.get(1)?.let { value ->
                when (value.lowercase(Locale.ROOT)) {
                    "malware" -> NetShieldCategory.MALWARE
                    "ads" -> NetShieldCategory.ADS
                    "trackers" -> NetShieldCategory.TRACKERS
                    "adult" -> NetShieldCategory.ADULT
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
        private val SOURCES = listOf(
            Source(NetShieldCategory.MALWARE, "https://urlhaus.abuse.ch/downloads/hostfile/", "malware"),
            Source(NetShieldCategory.ADS, "https://adaway.org/hosts.txt", "ads"),
            Source(NetShieldCategory.TRACKERS, "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/light.txt", "trackers"),
            Source(NetShieldCategory.ADULT, "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts", "adult"),
        )
    }
}
