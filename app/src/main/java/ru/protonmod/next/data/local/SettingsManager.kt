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

package ru.protonmod.next.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.core.MultiProcessDataStoreFactory
import java.io.File
import ru.protonmod.next.utils.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.netshield.NetShieldStats
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.utils.system.SystemUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore: DataStore<Preferences> = getOrCreateDataStore(context)
    private val prefs = context.getSharedPreferences("boot_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var dataStoreInstance: DataStore<Preferences>? = null

        private fun getOrCreateDataStore(context: Context): DataStore<Preferences> =
            dataStoreInstance ?: synchronized(this) {
                dataStoreInstance ?: MultiProcessDataStoreFactory.create(
                    serializer = PreferencesFileSerializer,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = {
                        File(context.applicationContext.filesDir, "datastore/settings.preferences_pb")
                    }
                ).also { dataStoreInstance = it }
            }

        const val STRATEGY_NETLIFY = "netlify"
        const val STRATEGY_CLOUDFLARE = "cloudflare"
        const val STRATEGY_DENO = "deno"
        const val STRATEGY_PROTON_MIRRORS = "proton_mirrors"
        const val STRATEGY_BYEDPI = "byedpi"
        const val STRATEGY_CUSTOM_PROXY = "custom_proxy"

        const val PROXY_TYPE_HTTP = "http"
        const val PROXY_TYPE_SOCKS = "socks"

        private val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications")
        private val CONNECTION_VERIFICATION_MODE = stringPreferencesKey("connection_verification_mode")
        private val CONNECTION_VERIFICATION_REQUIRED = booleanPreferencesKey("connection_verification_required")
        private val HANDSHAKE_RECONNECT_TIMEOUT_SECONDS = intPreferencesKey("handshake_reconnect_timeout_seconds")
        private val CONNECTION_PREFLIGHT_REQUIRED = booleanPreferencesKey("connection_preflight_required")
        private val CONNECTION_FAILURE_DETECTION = booleanPreferencesKey("connection_failure_detection")
        private val CONNECTION_AUTO_RECONNECT = booleanPreferencesKey("connection_auto_reconnect")

        private val OTA_UPDATE_FREQUENCY = stringPreferencesKey("ota_update_frequency") // "hourly", "daily", "weekly", "monthly", "disabled"
        private val OTA_LAST_CHECK_TIME = androidx.datastore.preferences.core.longPreferencesKey("ota_last_check_time_v2")

        private val APP_THEME = stringPreferencesKey("app_theme")
        private val SERVER_LOAD_DISPLAY_MODE = stringPreferencesKey("server_load_display_mode")

        private val SPLIT_TUNNELING_ENABLED = booleanPreferencesKey("split_tunneling_enabled")
        private val SPLIT_TUNNELING_MODE = stringPreferencesKey("split_tunneling_mode") // "exclude" or "include"
        private val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val EXCLUDED_IPS = stringSetPreferencesKey("excluded_ips")
        private val EXCLUDED_DOMAINS = stringSetPreferencesKey("excluded_domains")

        private val ST_SHOW_SYSTEM_APPS = booleanPreferencesKey("st_show_system_apps")

        private val VPN_PORT = intPreferencesKey("vpn_port")

        // Custom DNS IP setting (IPv4 or IPv6)
        private val CUSTOM_DNS = stringPreferencesKey("custom_dns")

        // API Bypass Settings
        private val API_BYPASS_ENABLED = booleanPreferencesKey("api_bypass_enabled")
        private val API_BYPASS_STRATEGY = stringPreferencesKey("api_bypass_strategy")

        private val BYEDPI_FLAGS = stringPreferencesKey("byedpi_flags")
        private val BYEDPI_SNI = stringPreferencesKey("byedpi_sni")

        private val API_PROXY_HOST = stringPreferencesKey("api_proxy_host")
        private val API_PROXY_PORT = intPreferencesKey("api_proxy_port")
        private val API_PROXY_TYPE = stringPreferencesKey("api_proxy_type") // "http" or "socks"
        private val API_PROXY_USERNAME = stringPreferencesKey("api_proxy_username")
        private val API_PROXY_PASSWORD = stringPreferencesKey("api_proxy_password")

        // API Mirroring / Spoofing Settings
        private val SPOOF_COUNTRY_ENABLED = booleanPreferencesKey("spoof_country_enabled")
        private val SPOOF_COUNTRY_NULL = booleanPreferencesKey("spoof_country_null")
        private val SPOOF_COUNTRY_CODE = stringPreferencesKey("spoof_country_code")

        private val OBFUSCATION_ENABLED = booleanPreferencesKey("obfuscation_enabled")
        private val OBFUSCATION_ADVANCED_MODE = booleanPreferencesKey("obfuscation_advanced_mode")
        private val PROXY_CHAIN_ENABLED = booleanPreferencesKey("proxy_chain_enabled")
        private val PROXY_CHAIN_CONFIG = stringPreferencesKey("proxy_chain_config")
        private val TOR_MODE_ENABLED = booleanPreferencesKey("tor_mode_enabled")
        private val IP_ROTATION_ENABLED = booleanPreferencesKey("ip_rotation_enabled")
        private val IP_ROTATION_INTERVAL_MINUTES = intPreferencesKey("ip_rotation_interval_minutes")
        private val IP_ROTATION_KEEP_COUNTRY = booleanPreferencesKey("ip_rotation_keep_country")
        private val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles")

        private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        private val TRAFFIC_STATS_ENABLED = booleanPreferencesKey("traffic_stats_enabled")
        private val NETSHIELD_LEVEL = stringPreferencesKey("netshield_level")
        private val NETSHIELD_MALWARE_BLOCKED = longPreferencesKey("netshield_malware_blocked")
        private val NETSHIELD_ADS_BLOCKED = longPreferencesKey("netshield_ads_blocked")
        private val NETSHIELD_TRACKERS_BLOCKED = longPreferencesKey("netshield_trackers_blocked")
        private val NETSHIELD_SAVED_BYTES = longPreferencesKey("netshield_saved_bytes")
        private val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        private val SENTRY_PERFORMANCE_ENABLED = booleanPreferencesKey("sentry_performance_enabled")
        private val SENTRY_NON_FATAL_ENABLED = booleanPreferencesKey("sentry_non_fatal_enabled")
        private val SENTRY_SESSION_REPLAY_ENABLED = booleanPreferencesKey("sentry_session_replay_enabled")
        private val SENTRY_ANR_ENABLED = booleanPreferencesKey("sentry_anr_enabled")
        private val SENTRY_METRICS_ENABLED = booleanPreferencesKey("sentry_metrics_enabled")
        private val SENTRY_LOGS_ENABLED = booleanPreferencesKey("sentry_logs_enabled")

        private val QUICK_CONNECT_STRATEGY = stringPreferencesKey("quick_connect_strategy") // "fastest", "recent", "profile"
        private val QUICK_CONNECT_TARGET_ID = stringPreferencesKey("quick_connect_target_id")

        private val IP_HIDDEN = booleanPreferencesKey("is_ip_hidden")

        private val PAUSE_END_TIME = androidx.datastore.preferences.core.longPreferencesKey("pause_end_time_v2")

        private val POLICY_ACCEPTED_VERSION = intPreferencesKey("policy_accepted_version")
        const val CURRENT_POLICY_VERSION = 20260625

        private val SETUP_STEP = stringPreferencesKey("setup_step")

        private val ALLOW_LAN_CONNECTIONS = booleanPreferencesKey("allow_lan_connections")

        private val RECONNECT_HINT_ENABLED = booleanPreferencesKey("reconnect_hint_enabled")

        private val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val AI_MODEL = stringPreferencesKey("ai_model")
        private val AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val AI_BYPASS_BLOCKS = booleanPreferencesKey("ai_bypass_blocks")

        private val AWG_JC = intPreferencesKey("awg_jc")
        private val AWG_JMIN = intPreferencesKey("awg_jmin")
        private val AWG_JMAX = intPreferencesKey("awg_jmax")
        private val AWG_S1 = intPreferencesKey("awg_s1")
        private val AWG_S2 = intPreferencesKey("awg_s2")
        private val AWG_S3 = intPreferencesKey("awg_s3") // cookieReplyPacketJunkSize
        private val AWG_S4 = intPreferencesKey("awg_s4") // transportPacketJunkSize
        private val AWG_H1 = stringPreferencesKey("awg_h1")
        private val AWG_H2 = stringPreferencesKey("awg_h2")
        private val AWG_H3 = stringPreferencesKey("awg_h3")
        private val AWG_H4 = stringPreferencesKey("awg_h4")
        private val AWG_I1 = stringPreferencesKey("awg_i1")
        private val AWG_I2 = stringPreferencesKey("awg_i2")
        private val AWG_I3 = stringPreferencesKey("awg_i3")
        private val AWG_I4 = stringPreferencesKey("awg_i4")
        private val AWG_I5 = stringPreferencesKey("awg_i5")
        private val AWG_HEADER_PROTECTION_KEY = stringPreferencesKey("awg_hp_key")
        private val AWG_CONTENT_PADDING_ADDITION = stringPreferencesKey("awg_cp_addition")
        private val AWG_REKEY_AFTER_TIME = stringPreferencesKey("awg_rekey_after")
        private val AWG_REKEY_TIMEOUT = stringPreferencesKey("awg_rekey_timeout")
        private val AWG_REJECT_AFTER_TIME = stringPreferencesKey("awg_reject_after")
        private val AWG_KEEPALIVE_TIMEOUT = stringPreferencesKey("awg_keepalive_timeout")
        private val AWG_MAX_HANDSHAKE_ATTEMPTS = stringPreferencesKey("awg_max_handshake")
        private val AWG_PERSISTENT_KEEPALIVE = stringPreferencesKey("awg_persistent_keepalive")
        private val AWG_JUNK_LEVEL = intPreferencesKey("awg_junk_level")

        const val DEFAULT_IP_ROTATION_INTERVAL_MINUTES = 15
        const val DEFAULT_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS = 5
        const val MIN_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS = 3
        const val MAX_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS = 30

        const val DEFAULT_I1 = "<b 0xce000000010897a297ecc34cd6dd000044d0ec2e2e1ea2991f467ace4222129b5a098823784694b4897b9986ae0b7280135fa85e196d9ad980b150122129ce2a9379531b0fd3e871ca5fdb883c369832f730e272d7b8b74f393f9f0fa43f11e510ecb2219a52984410c204cf875585340c62238e14ad04dff382f2c200e0ee22fe743b9c6b8b043121c5710ec289f471c91ee414fca8b8be8419ae8ce7ffc53837f6ade262891895f3f4cecd31bc93ac5599e18e4f01b472362b8056c3172b513051f8322d1062997ef4a383b01706598d08d48c221d30e74c7ce000cdad36b706b1bf9b0607c32ec4b3203a4ee21ab64df336212b9758280803fcab14933b0e7ee1e04a7becce3e2633f4852585c567894a5f9efe9706a151b615856647e8b7dba69ab357b3982f554549bef9256111b2d67afde0b496f16962d4957ff654232aa9e845b61463908309cfd9de0a6abf5f425f577d7e5f6440652aa8da5f73588e82e9470f3b21b27b28c649506ae1a7f5f15b876f56abc4615f49911549b9bb39dd804fde182bd2dcec0c33bad9b138ca07d4a4a1650a2c2686acea05727e2a78962a840ae428f55627516e73c83dd8893b02358e81b524b4d99fda6df52b3a8d7a5291326e7ac9d773c5b43b8444554ef5aea104a738ed650aa979674bbed38da58ac29d87c29d387d80b526065baeb073ce65f075ccb56e47533aef357dceaa8293a523c5f6f790be90e4731123d3c6152a70576e90b4ab5bc5ead01576c68ab633ff7d36dcde2a0b2c68897e1acfc4d6483aaaeb635dd63c96b2b6a7a2bfe042f6aed82e5363aa850aace12ee3b1a93f30d8ab9537df483152a5527faca21efc9981b304f11fc95336f5b9637b174c5a0659e2b22e159a9fed4b8e93047371175b1d6d9cc8ab745f3b2281537d1c75fb9451871864efa5d184c38c185fd203de206751b92620f7c369e031d2041e152040920ac2c5ab5340bfc9d0561176abf10a147287ea90758575ac6a9f5ac9f390d0d5b23ee12af583383d994e22c0cf42383834bcd3ada1b3825a0664d8f3fb678261d57601ddf94a8a68a7c273a18c08aa99c7ad8c6c42eab67718843597ec9930457359dfdfbce024afc2dcf9348579a57d8d3490b2fa99f278f1c37d87dad9b221acd575192ffae1784f8e60ec7cee4068b6b988f0433d96d6a1b1865f4e155e9fe020279f434f3bf1bd117b717b92f6cd1cc9bea7d45978bcc3f24bda631a36910110a6ec06da35f8966c9279d130347594f13e9e07514fa370754d1424c0a1545c5070ef9fb2acd14233e8a50bfc5978b5bdf8bc1714731f798d21e2004117c61f2989dd44f0cf027b27d4019e81ed4b5c31db347c4a3a4d85048d7093cf16753d7b0d15e078f5c7a5205dc2f87e330a1f716738dce1c6180e9d02869b5546f1c4d2748f8c90d9693cba4e0079297d22fd61402dea32ff0eb69ebd65a5d0b687d87e3a8b2c42b648aa723c7c7daf37abcc4bb85caea2ee8f55bec20e913b3324ab8f5c3304f820d42ad1b9f2ffc1a3af9927136b4419e1e579ab4c2ae3c776d293d397d575df181e6cae0a4ada5d67ecea171cca3288d57c7bbdaee3befe745fb7d634f70386d873b90c4d6c6596bb65af68f9e5121e67ebf0d89d3c909ceedfb32ce9575a7758ff080724e1ab5d5f43074ecb53a479af21ed03d7b6899c36631c0166f9d47e5e1d4528a5d3d3f744029c4b1c190cbfbad06f5f83f7ad0429fa9a2719c56ffe3783460e166de2d8>"
    }

    /**
     * Emits whenever a setting that is only consumed while a tunnel is being established changes.
     * Settings pushed to a running service (kill switch, notifications, verification) never emit,
     * because those apply without a reconnect.
     */
    private val _connectionConfigChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val connectionConfigChanged: SharedFlow<Unit> = _connectionConfigChanged.asSharedFlow()

    /**
     * Stores a connection-time setting and signals [connectionConfigChanged] only when the stored
     * value really differs, so screens that rewrite their current values on entry stay silent.
     */
    private suspend fun <T> editConnectionSetting(key: Preferences.Key<T>, default: T, value: T) {
        var changed = false
        dataStore.edit { preferences ->
            changed = (preferences[key] ?: default) != value
            preferences[key] = value
        }
        if (changed) _connectionConfigChanged.tryEmit(Unit)
    }

    val killSwitchEnabled: Flow<Boolean> = dataStore.data.map { it[KILL_SWITCH] ?: false }
    val autoConnectEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_CONNECT] ?: false }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS] ?: true }
    val connectionVerificationMode: Flow<ConnectionVerificationMode> = dataStore.data.map { preferences ->
        runCatching {
            ConnectionVerificationMode.valueOf(
                preferences[CONNECTION_VERIFICATION_MODE] ?: ConnectionVerificationMode.BALANCED.name
            )
        }.getOrDefault(ConnectionVerificationMode.BALANCED)
    }
    val connectionVerificationRequired: Flow<Boolean> = dataStore.data.map {
        it[CONNECTION_VERIFICATION_REQUIRED] ?: false
    }
    val handshakeReconnectTimeoutSeconds: Flow<Int> = dataStore.data.map {
        (it[HANDSHAKE_RECONNECT_TIMEOUT_SECONDS] ?: DEFAULT_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS)
            .coerceIn(MIN_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS, MAX_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS)
    }
    val connectionPreflightRequired: Flow<Boolean> = dataStore.data.map {
        it[CONNECTION_PREFLIGHT_REQUIRED] ?: false
    }
    val connectionFailureDetection: Flow<Boolean> = dataStore.data.map {
        it[CONNECTION_FAILURE_DETECTION] ?: true
    }
    val connectionAutoReconnect: Flow<Boolean> = dataStore.data.map {
        it[CONNECTION_AUTO_RECONNECT] ?: true
    }

    val defaultTheme: AppTheme
        get() = if (SystemUtils.isNothingDevice()) AppTheme.NOTHING else AppTheme.SYSTEM

    val otaUpdateFrequency: Flow<String> = dataStore.data.map { it[OTA_UPDATE_FREQUENCY] ?: "daily" }
    val otaLastCheckTime: Flow<Long> = dataStore.data.map { it[OTA_LAST_CHECK_TIME] ?: 0L }

    val appTheme: Flow<ru.protonmod.next.ui.theme.AppTheme> = dataStore.data.map { preferences ->
        val themeString = preferences[APP_THEME] ?: return@map defaultTheme
        try {
            ru.protonmod.next.ui.theme.AppTheme.valueOf(themeString)
        } catch (e: Exception) {
            defaultTheme
        }
    }

    val serverLoadDisplayMode: Flow<ServerLoadDisplayMode> = dataStore.data.map { preferences ->
        val modeString = preferences[SERVER_LOAD_DISPLAY_MODE] ?: ServerLoadDisplayMode.ALL.name
        try {
            ServerLoadDisplayMode.valueOf(modeString)
        } catch (e: Exception) {
            ServerLoadDisplayMode.ALL
        }
    }

    val splitTunnelingEnabled: Flow<Boolean> = dataStore.data.map { it[SPLIT_TUNNELING_ENABLED] ?: false }
    val splitTunnelingMode: Flow<String> = dataStore.data.map { it[SPLIT_TUNNELING_MODE] ?: "exclude" }
    val excludedApps: Flow<Set<String>> = dataStore.data.map { it[EXCLUDED_APPS] ?: emptySet() }
    val excludedIps: Flow<Set<String>> = dataStore.data.map { it[EXCLUDED_IPS] ?: emptySet() }
    val excludedDomains: Flow<Set<String>> = dataStore.data.map { it[EXCLUDED_DOMAINS] ?: emptySet() }

    val stShowSystemApps: Flow<Boolean> = dataStore.data.map { it[ST_SHOW_SYSTEM_APPS] ?: false }

    val vpnPort: Flow<Int> = dataStore.data.map { it[VPN_PORT] ?: 0 }
    val customDns: Flow<String> = dataStore.data.map { it[CUSTOM_DNS] ?: "" }

    val apiBypassEnabled: Flow<Boolean> = dataStore.data.map { it[API_BYPASS_ENABLED] ?: false }
    val apiBypassStrategy: Flow<String> = dataStore.data.map { it[API_BYPASS_STRATEGY] ?: "netlify" }

    val byeDpiFlags: Flow<String> = dataStore.data.map { it[BYEDPI_FLAGS] ?: "-s1 -d1" }
    val byeDpiSni: Flow<String> = dataStore.data.map { it[BYEDPI_SNI] ?: "google.com" }

    val apiProxyHost: Flow<String> = dataStore.data.map { it[API_PROXY_HOST] ?: "" }
    val apiProxyPort: Flow<Int> = dataStore.data.map { it[API_PROXY_PORT] ?: 1080 }
    val apiProxyType: Flow<String> = dataStore.data.map { it[API_PROXY_TYPE] ?: PROXY_TYPE_SOCKS }
    val apiProxyUsername: Flow<String> = dataStore.data.map { it[API_PROXY_USERNAME] ?: "" }
    val apiProxyPassword: Flow<String> = dataStore.data.map { it[API_PROXY_PASSWORD] ?: "" }

    val spoofCountryEnabled: Flow<Boolean> = dataStore.data.map { it[SPOOF_COUNTRY_ENABLED] ?: false }
    val spoofCountryNull: Flow<Boolean> = dataStore.data.map { it[SPOOF_COUNTRY_NULL] ?: false }
    val spoofCountryCode: Flow<String> = dataStore.data.map { it[SPOOF_COUNTRY_CODE] ?: "" }

    val obfuscationEnabled: Flow<Boolean> = dataStore.data.map { it[OBFUSCATION_ENABLED] ?: false }
    val obfuscationAdvancedMode: Flow<Boolean> = dataStore.data.map { it[OBFUSCATION_ADVANCED_MODE] ?: false }
    val proxyChainEnabled: Flow<Boolean> = dataStore.data.map { it[PROXY_CHAIN_ENABLED] ?: false }
    val proxyChainConfig: Flow<String> = dataStore.data.map { it[PROXY_CHAIN_CONFIG] ?: "" }
    val torModeEnabled: Flow<Boolean> = dataStore.data.map { it[TOR_MODE_ENABLED] ?: false }
    val ipRotationEnabled: Flow<Boolean> = dataStore.data.map { it[IP_ROTATION_ENABLED] ?: false }
    val ipRotationIntervalMinutes: Flow<Int> = dataStore.data.map {
        (it[IP_ROTATION_INTERVAL_MINUTES] ?: DEFAULT_IP_ROTATION_INTERVAL_MINUTES).coerceIn(5, 60)
    }
    val ipRotationKeepCountry: Flow<Boolean> = dataStore.data.map { it[IP_ROTATION_KEEP_COUNTRY] ?: true }
    val selectedProfileId: Flow<String> = dataStore.data.map { it[SELECTED_PROFILE_ID] ?: "standard_1" }

    val setupStep: Flow<SetupStep> = dataStore.data.map { preferences ->
        val stepString = preferences[SETUP_STEP] ?: SetupStep.WELCOME.name
        try {
            SetupStep.valueOf(stepString)
        } catch (e: Exception) {
            SetupStep.WELCOME
        }
    }

    val allowLanEnabled: Flow<Boolean> = dataStore.data.map { it[ALLOW_LAN_CONNECTIONS] ?: false }

    /** Whether the "changes apply after reconnect" notice is shown while the tunnel is up. */
    val reconnectHintEnabled: Flow<Boolean> = dataStore.data.map { it[RECONNECT_HINT_ENABLED] ?: true }

    val aiEnabled: Flow<Boolean> = dataStore.data.map { it[AI_ENABLED] ?: false }
    val aiProvider: Flow<String> = dataStore.data.map { it[AI_PROVIDER] ?: "openai" }
    val aiModel: Flow<String> = dataStore.data.map { it[AI_MODEL] ?: "" }
    val aiApiKey: Flow<String> = dataStore.data.map { it[AI_API_KEY] ?: "" }
    val aiBypassBlocks: Flow<Boolean> = dataStore.data.map { it[AI_BYPASS_BLOCKS] ?: true }

    // Privacy-first defaults: only crash reports and handled errors are enabled.
    // All optional telemetry requires an explicit user opt-in.
    val analyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ANALYTICS_ENABLED] ?: false }
    val crashReportsEnabled: Flow<Boolean> = dataStore.data.map { it[CRASH_REPORTS_ENABLED] ?: true }
    val sentryPerformanceEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_PERFORMANCE_ENABLED] ?: false }
    val sentryNonFatalEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_NON_FATAL_ENABLED] ?: true }
    val sentrySessionReplayEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_SESSION_REPLAY_ENABLED] ?: false }
    val sentryAnrEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_ANR_ENABLED] ?: false }
    val sentryMetricsEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_METRICS_ENABLED] ?: false }
    val sentryLogsEnabled: Flow<Boolean> = dataStore.data.map { it[SENTRY_LOGS_ENABLED] ?: false }

    /** Synchronous check for app startup initializers to avoid ANR from runBlocking */
    fun isAnalyticsEnabledSync(): Boolean = prefs.getBoolean("analytics_enabled", false)

    /** Synchronous check for app startup initializers to avoid ANR from runBlocking */
    fun isCrashReportsEnabledSync(): Boolean = prefs.getBoolean("crash_reports_enabled", true)

    fun isPerformanceEnabledSync(): Boolean = prefs.getBoolean("sentry_performance_enabled", false)
    fun isNonFatalEnabledSync(): Boolean = prefs.getBoolean("sentry_non_fatal_enabled", true)
    fun isSessionReplayEnabledSync(): Boolean = prefs.getBoolean("sentry_session_replay_enabled", false)
    fun isAnrEnabledSync(): Boolean = prefs.getBoolean("sentry_anr_enabled", false)
    fun isMetricsEnabledSync(): Boolean = prefs.getBoolean("sentry_metrics_enabled", false)
    fun isLogsEnabledSync(): Boolean = prefs.getBoolean("sentry_logs_enabled", false)

    fun isApiBypassEnabledSync(): Boolean = prefs.getBoolean("api_bypass_enabled", false)
    fun getApiBypassStrategySync(): String {
        return prefs.getString("api_bypass_strategy", "netlify") ?: "netlify"
    }

    fun getByeDpiFlagsSync(): String = prefs.getString("byedpi_flags", "-s1 -d1") ?: "-s1 -d1"
    fun getByeDpiSniSync(): String = prefs.getString("byedpi_sni", "google.com") ?: "google.com"

    fun getApiProxyHostSync(): String = prefs.getString("api_proxy_host", "") ?: ""
    fun getApiProxyPortSync(): Int = prefs.getInt("api_proxy_port", 1080)
    fun getApiProxyTypeSync(): String = prefs.getString("api_proxy_type", PROXY_TYPE_SOCKS) ?: PROXY_TYPE_SOCKS
    fun getApiProxyUsernameSync(): String = prefs.getString("api_proxy_username", "") ?: ""
    fun getApiProxyPasswordSync(): String = prefs.getString("api_proxy_password", "") ?: ""

    fun isSpoofCountryEnabledSync(): Boolean = prefs.getBoolean("spoof_country_enabled", false)
    fun isSpoofCountryNullSync(): Boolean = prefs.getBoolean("spoof_country_null", false)
    fun getSpoofCountryCodeSync(): String = prefs.getString("spoof_country_code", "") ?: ""

    val quickConnectStrategy: Flow<String> = dataStore.data.map { it[QUICK_CONNECT_STRATEGY] ?: "fastest" }
    val quickConnectTargetId: Flow<String?> = dataStore.data.map { it[QUICK_CONNECT_TARGET_ID] }
    val isIpHidden: Flow<Boolean> = dataStore.data.map { it[IP_HIDDEN] ?: false }

    /** Dashboard traffic statistics collection toggle (mirrors desktop traffic_stats_enabled). */
    val trafficStatsEnabled: Flow<Boolean> = dataStore.data.map { it[TRAFFIC_STATS_ENABLED] ?: true }

    val netShieldLevel: Flow<NetShieldLevel> = dataStore.data.map { preferences ->
        runCatching { NetShieldLevel.valueOf(preferences[NETSHIELD_LEVEL] ?: NetShieldLevel.DISABLED.name) }
            .getOrDefault(NetShieldLevel.DISABLED)
    }

    val netShieldStats: Flow<NetShieldStats> = dataStore.data.map { preferences ->
        NetShieldStats(
            malwareBlocked = preferences[NETSHIELD_MALWARE_BLOCKED] ?: 0L,
            adsBlocked = preferences[NETSHIELD_ADS_BLOCKED] ?: 0L,
            trackersBlocked = preferences[NETSHIELD_TRACKERS_BLOCKED] ?: 0L,
            savedBytes = preferences[NETSHIELD_SAVED_BYTES] ?: 0L,
        )
    }

    val pauseEndTime: Flow<Long> = dataStore.data.map { it[PAUSE_END_TIME] ?: 0L }

    val policyAcceptedVersion: Flow<Int> = dataStore.data.map { it[POLICY_ACCEPTED_VERSION] ?: 0 }

    val customProfiles: Flow<List<ObfuscationProfile>> = dataStore.data.map { preferences ->
        val jsonString = preferences[CUSTOM_PROFILES] ?: "[]"
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ObfuscationProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ObfuscationProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        isReadOnly = obj.optBoolean("isReadOnly", false),
                        jc = obj.optInt("jc", 3),
                        jmin = obj.optInt("jmin", 1),
                        jmax = obj.optInt("jmax", 3),
                        s1 = obj.optInt("s1", 0),
                        s2 = obj.optInt("s2", 0),
                        s3 = obj.optInt("s3", 0),
                        s4 = obj.optInt("s4", 0),
                        h1 = obj.optString("h1", "1"),
                        h2 = obj.optString("h2", "2"),
                        h3 = obj.optString("h3", "3"),
                        h4 = obj.optString("h4", "4"),
                        i1 = obj.optString("i1", DEFAULT_I1),
                        i2 = obj.optString("i2", ""),
                        i3 = obj.optString("i3", ""),
                        i4 = obj.optString("i4", ""),
                        i5 = obj.optString("i5", ""),
                        headerProtectionKey = obj.optString("headerProtectionKey", ""),
                        contentPaddingAddition = obj.optString("contentPaddingAddition", ""),
                        rekeyAfterTime = obj.optString("rekeyAfterTime", ""),
                        rekeyTimeout = obj.optString("rekeyTimeout", ""),
                        rejectAfterTime = obj.optString("rejectAfterTime", ""),
                        keepaliveTimeout = obj.optString("keepaliveTimeout", ""),
                        maxHandshakeAttempts = obj.optString("maxHandshakeAttempts", ""),
                        persistentKeepaliveInterval = obj.optString("persistentKeepaliveInterval", ""),
                        junkLevel = obj.optInt("junkLevel", 3)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val awgJc: Flow<Int> = dataStore.data.map { it[AWG_JC] ?: 3 }
    val awgJmin: Flow<Int> = dataStore.data.map { it[AWG_JMIN] ?: 1 }
    val awgJmax: Flow<Int> = dataStore.data.map { it[AWG_JMAX] ?: 3 }
    val awgS1: Flow<Int> = dataStore.data.map { it[AWG_S1] ?: 0 }
    val awgS2: Flow<Int> = dataStore.data.map { it[AWG_S2] ?: 0 }
    val awgS3: Flow<Int> = dataStore.data.map { it[AWG_S3] ?: 0 }
    val awgS4: Flow<Int> = dataStore.data.map { it[AWG_S4] ?: 0 }
    val awgH1: Flow<String> = dataStore.data.map { it[AWG_H1] ?: "1" }
    val awgH2: Flow<String> = dataStore.data.map { it[AWG_H2] ?: "2" }
    val awgH3: Flow<String> = dataStore.data.map { it[AWG_H3] ?: "3" }
    val awgH4: Flow<String> = dataStore.data.map { it[AWG_H4] ?: "4" }
    val awgI1: Flow<String> = dataStore.data.map { it[AWG_I1] ?: DEFAULT_I1 }
    val awgI2: Flow<String> = dataStore.data.map { it[AWG_I2] ?: "" }
    val awgI3: Flow<String> = dataStore.data.map { it[AWG_I3] ?: "" }
    val awgI4: Flow<String> = dataStore.data.map { it[AWG_I4] ?: "" }
    val awgI5: Flow<String> = dataStore.data.map { it[AWG_I5] ?: "" }
    val awgHeaderProtectionKey: Flow<String> = dataStore.data.map { it[AWG_HEADER_PROTECTION_KEY] ?: "" }
    val awgContentPaddingAddition: Flow<String> = dataStore.data.map { it[AWG_CONTENT_PADDING_ADDITION] ?: "" }
    val awgRekeyAfterTime: Flow<String> = dataStore.data.map { it[AWG_REKEY_AFTER_TIME] ?: "" }
    val awgRekeyTimeout: Flow<String> = dataStore.data.map { it[AWG_REKEY_TIMEOUT] ?: "" }
    val awgRejectAfterTime: Flow<String> = dataStore.data.map { it[AWG_REJECT_AFTER_TIME] ?: "" }
    val awgKeepaliveTimeout: Flow<String> = dataStore.data.map { it[AWG_KEEPALIVE_TIMEOUT] ?: "" }
    val awgMaxHandshakeAttempts: Flow<String> = dataStore.data.map { it[AWG_MAX_HANDSHAKE_ATTEMPTS] ?: "" }
    val awgPersistentKeepalive: Flow<String> = dataStore.data.map { it[AWG_PERSISTENT_KEEPALIVE] ?: "" }
    val awgJunkLevel: Flow<Int> = dataStore.data.map { it[AWG_JUNK_LEVEL] ?: 0 }

    suspend fun setKillSwitch(enabled: Boolean) {
        dataStore.edit { it[KILL_SWITCH] = enabled }
    }

    suspend fun setNetShieldLevel(level: NetShieldLevel) {
        editConnectionSetting(NETSHIELD_LEVEL, NetShieldLevel.DISABLED.name, level.name)
    }

    suspend fun resetNetShieldStats() {
        dataStore.edit {
            it[NETSHIELD_MALWARE_BLOCKED] = 0L
            it[NETSHIELD_ADS_BLOCKED] = 0L
            it[NETSHIELD_TRACKERS_BLOCKED] = 0L
            it[NETSHIELD_SAVED_BYTES] = 0L
        }
    }

    suspend fun addNetShieldStats(malware: Long, ads: Long, trackers: Long, savedBytes: Long) {
        if (malware == 0L && ads == 0L && trackers == 0L && savedBytes == 0L) return
        dataStore.edit {
            it[NETSHIELD_MALWARE_BLOCKED] = (it[NETSHIELD_MALWARE_BLOCKED] ?: 0L) + malware
            it[NETSHIELD_ADS_BLOCKED] = (it[NETSHIELD_ADS_BLOCKED] ?: 0L) + ads
            it[NETSHIELD_TRACKERS_BLOCKED] = (it[NETSHIELD_TRACKERS_BLOCKED] ?: 0L) + trackers
            it[NETSHIELD_SAVED_BYTES] = (it[NETSHIELD_SAVED_BYTES] ?: 0L) + savedBytes
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    suspend fun setConnectionVerificationMode(mode: ConnectionVerificationMode) {
        dataStore.edit { it[CONNECTION_VERIFICATION_MODE] = mode.name }
    }

    suspend fun setConnectionVerificationRequired(required: Boolean) {
        dataStore.edit { it[CONNECTION_VERIFICATION_REQUIRED] = required }
    }

    suspend fun setHandshakeReconnectTimeoutSeconds(seconds: Int) {
        dataStore.edit {
            it[HANDSHAKE_RECONNECT_TIMEOUT_SECONDS] = seconds.coerceIn(
                MIN_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS,
                MAX_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS,
            )
        }
    }

    suspend fun setConnectionPreflightRequired(required: Boolean) {
        dataStore.edit { it[CONNECTION_PREFLIGHT_REQUIRED] = required }
    }

    suspend fun setConnectionFailureDetection(enabled: Boolean) {
        dataStore.edit { it[CONNECTION_FAILURE_DETECTION] = enabled }
    }

    suspend fun setConnectionAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[CONNECTION_AUTO_RECONNECT] = enabled }
    }

    suspend fun setOtaUpdateFrequency(frequency: String) {
        dataStore.edit { it[OTA_UPDATE_FREQUENCY] = frequency }
    }

    suspend fun setOtaLastCheckTime(time: Long) {
        dataStore.edit { it[OTA_LAST_CHECK_TIME] = time }
    }

    suspend fun setAppTheme(theme: ru.protonmod.next.ui.theme.AppTheme) {
        dataStore.edit { it[APP_THEME] = theme.name }
    }

    suspend fun setServerLoadDisplayMode(mode: ServerLoadDisplayMode) {
        dataStore.edit { it[SERVER_LOAD_DISPLAY_MODE] = mode.name }
    }

    suspend fun setSplitTunnelingEnabled(enabled: Boolean) {
        editConnectionSetting(SPLIT_TUNNELING_ENABLED, false, enabled)
    }

    suspend fun setSplitTunnelingMode(mode: String) {
        editConnectionSetting(SPLIT_TUNNELING_MODE, "exclude", mode)
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        editConnectionSetting(EXCLUDED_APPS, emptySet(), apps)
    }

    suspend fun setExcludedIps(ips: Set<String>) {
        editConnectionSetting(EXCLUDED_IPS, emptySet(), ips)
    }

    suspend fun setExcludedDomains(domains: Set<String>) {
        editConnectionSetting(EXCLUDED_DOMAINS, emptySet(), domains)
    }

    suspend fun setStShowSystemApps(enabled: Boolean) {
        dataStore.edit { it[ST_SHOW_SYSTEM_APPS] = enabled }
    }

    suspend fun setVpnPort(port: Int) {
        editConnectionSetting(VPN_PORT, 0, port)
    }

    suspend fun setCustomDns(dnsIp: String) {
        editConnectionSetting(CUSTOM_DNS, "", dnsIp)
    }

    suspend fun setApiBypassEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("api_bypass_enabled", enabled) }
        dataStore.edit { it[API_BYPASS_ENABLED] = enabled }
    }

    suspend fun setApiBypassStrategy(strategy: String) {
        ProtonLogger.d("SettingsManager", "Setting strategy to: $strategy")
        prefs.edit { putString("api_bypass_strategy", strategy) }
        dataStore.edit { it[API_BYPASS_STRATEGY] = strategy }
    }

    suspend fun setByeDpiFlags(flags: String) {
        prefs.edit { putString("byedpi_flags", flags) }
        dataStore.edit { it[BYEDPI_FLAGS] = flags }
    }

    suspend fun setByeDpiSni(sni: String) {
        prefs.edit { putString("byedpi_sni", sni) }
        dataStore.edit { it[BYEDPI_SNI] = sni }
    }

    suspend fun setApiProxyHost(host: String) {
        prefs.edit { putString("api_proxy_host", host) }
        dataStore.edit { it[API_PROXY_HOST] = host }
    }

    suspend fun setApiProxyPort(port: Int) {
        prefs.edit { putInt("api_proxy_port", port) }
        dataStore.edit { it[API_PROXY_PORT] = port }
    }

    suspend fun setApiProxyType(type: String) {
        prefs.edit { putString("api_proxy_type", type) }
        dataStore.edit { it[API_PROXY_TYPE] = type }
    }

    suspend fun setApiProxyUsername(username: String) {
        prefs.edit { putString("api_proxy_username", username) }
        dataStore.edit { it[API_PROXY_USERNAME] = username }
    }

    suspend fun setApiProxyPassword(password: String) {
        prefs.edit { putString("api_proxy_password", password) }
        dataStore.edit { it[API_PROXY_PASSWORD] = password }
    }

    suspend fun setSpoofCountryEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("spoof_country_enabled", enabled) }
        dataStore.edit { it[SPOOF_COUNTRY_ENABLED] = enabled }
    }

    suspend fun setSpoofCountryNull(enabled: Boolean) {
        prefs.edit { putBoolean("spoof_country_null", enabled) }
        dataStore.edit { it[SPOOF_COUNTRY_NULL] = enabled }
    }

    suspend fun setSpoofCountryCode(code: String) {
        prefs.edit { putString("spoof_country_code", code) }
        dataStore.edit { it[SPOOF_COUNTRY_CODE] = code }
    }

    suspend fun setObfuscationEnabled(enabled: Boolean) {
        editConnectionSetting(OBFUSCATION_ENABLED, false, enabled)
    }

    suspend fun setObfuscationAdvancedMode(enabled: Boolean) {
        dataStore.edit { it[OBFUSCATION_ADVANCED_MODE] = enabled }
    }

    suspend fun setProxyChainEnabled(enabled: Boolean) {
        var changed = false
        dataStore.edit {
            changed = (it[PROXY_CHAIN_ENABLED] ?: false) != enabled
            it[PROXY_CHAIN_ENABLED] = enabled
            if (enabled) {
                changed = changed || (it[OBFUSCATION_ENABLED] ?: false)
                it[OBFUSCATION_ENABLED] = false
            }
        }
        if (changed) _connectionConfigChanged.tryEmit(Unit)
    }

    suspend fun setProxyChainConfig(config: String) {
        editConnectionSetting(PROXY_CHAIN_CONFIG, "", config.trim())
    }

    suspend fun setTorModeEnabled(enabled: Boolean) {
        editConnectionSetting(TOR_MODE_ENABLED, false, enabled)
    }

    suspend fun setIpRotationEnabled(enabled: Boolean) {
        dataStore.edit { it[IP_ROTATION_ENABLED] = enabled }
    }

    suspend fun setIpRotationIntervalMinutes(minutes: Int) {
        require(minutes in setOf(5, 15, 30, 60)) { "Unsupported IP rotation interval: $minutes" }
        dataStore.edit { it[IP_ROTATION_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setIpRotationKeepCountry(keepCountry: Boolean) {
        dataStore.edit { it[IP_ROTATION_KEEP_COUNTRY] = keepCountry }
    }

    suspend fun setAllowLanEnabled(enabled: Boolean) {
        editConnectionSetting(ALLOW_LAN_CONNECTIONS, false, enabled)
    }

    suspend fun setReconnectHintEnabled(enabled: Boolean) {
        dataStore.edit { it[RECONNECT_HINT_ENABLED] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_ENABLED] = enabled }
    }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { it[AI_PROVIDER] = provider }
    }

    suspend fun setAiModel(model: String) {
        dataStore.edit { it[AI_MODEL] = model }
    }

    suspend fun setAiApiKey(key: String) {
        dataStore.edit { it[AI_API_KEY] = key }
    }

    suspend fun setAiBypassBlocks(enabled: Boolean) {
        dataStore.edit { it[AI_BYPASS_BLOCKS] = enabled }
    }

    suspend fun setSelectedProfileId(id: String) {
        dataStore.edit { it[SELECTED_PROFILE_ID] = id }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("analytics_enabled", enabled) }
        dataStore.edit { it[ANALYTICS_ENABLED] = enabled }
        ProtonLogger.isAnalyticsEnabled = enabled
    }

    suspend fun setCrashReportsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("crash_reports_enabled", enabled) }
        dataStore.edit { it[CRASH_REPORTS_ENABLED] = enabled }
    }

    suspend fun setSentryPerformanceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_performance_enabled", enabled) }
        dataStore.edit { it[SENTRY_PERFORMANCE_ENABLED] = enabled }
    }

    suspend fun setSentryNonFatalEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_non_fatal_enabled", enabled) }
        dataStore.edit { it[SENTRY_NON_FATAL_ENABLED] = enabled }
        ProtonLogger.isNonFatalEnabled = enabled
    }

    suspend fun setSentrySessionReplayEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_session_replay_enabled", enabled) }
        dataStore.edit { it[SENTRY_SESSION_REPLAY_ENABLED] = enabled }
    }

    suspend fun setSentryAnrEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_anr_enabled", enabled) }
        dataStore.edit { it[SENTRY_ANR_ENABLED] = enabled }
    }

    suspend fun setSentryMetricsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_metrics_enabled", enabled) }
        dataStore.edit { it[SENTRY_METRICS_ENABLED] = enabled }
    }

    suspend fun setSentryLogsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_logs_enabled", enabled) }
        dataStore.edit { it[SENTRY_LOGS_ENABLED] = enabled }
    }

    suspend fun setPolicyAcceptedVersion(version: Int) {
        ProtonLogger.d("SettingsManager", "Saving policy accepted version: $version")
        dataStore.edit { it[POLICY_ACCEPTED_VERSION] = version }
        ProtonLogger.d("SettingsManager", "Policy accepted version saved.")
    }

    suspend fun setQuickConnectStrategy(strategy: String, targetId: String? = null) {
        dataStore.edit {
            it[QUICK_CONNECT_STRATEGY] = strategy
            if (targetId != null) {
                it[QUICK_CONNECT_TARGET_ID] = targetId
            } else {
                it.remove(QUICK_CONNECT_TARGET_ID)
            }
        }
    }

    suspend fun setIpHidden(hidden: Boolean) {
        dataStore.edit { it[IP_HIDDEN] = hidden }
    }

    suspend fun setTrafficStatsEnabled(enabled: Boolean) {
        dataStore.edit { it[TRAFFIC_STATS_ENABLED] = enabled }
    }

    suspend fun setPauseEndTime(time: Long) {
        dataStore.edit { it[PAUSE_END_TIME] = time }
    }

    suspend fun setSetupStep(step: SetupStep) {
        dataStore.edit { it[SETUP_STEP] = step.name }
    }

    suspend fun saveCustomProfiles(profiles: List<ObfuscationProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isReadOnly", p.isReadOnly)
                put("jc", p.jc)
                put("jmin", p.jmin)
                put("jmax", p.jmax)
                put("s1", p.s1)
                put("s2", p.s2)
                put("s3", p.s3)
                put("s4", p.s4)
                put("h1", p.h1)
                put("h2", p.h2)
                put("h3", p.h3)
                put("h4", p.h4)
                put("i1", p.i1)
                put("i2", p.i2)
                put("i3", p.i3)
                put("i4", p.i4)
                put("i5", p.i5)
                put("headerProtectionKey", p.headerProtectionKey)
                put("contentPaddingAddition", p.contentPaddingAddition)
                put("rekeyAfterTime", p.rekeyAfterTime)
                put("rekeyTimeout", p.rekeyTimeout)
                put("rejectAfterTime", p.rejectAfterTime)
                put("keepaliveTimeout", p.keepaliveTimeout)
                put("maxHandshakeAttempts", p.maxHandshakeAttempts)
                put("persistentKeepaliveInterval", p.persistentKeepaliveInterval)
                put("junkLevel", p.junkLevel)
            }
            array.put(obj)
        }
        dataStore.edit { it[CUSTOM_PROFILES] = array.toString() }
    }

    suspend fun setAwgParam(keyName: String, value: Any) {
        val key = when (keyName) {
            "jc" -> AWG_JC
            "jmin" -> AWG_JMIN
            "jmax" -> AWG_JMAX
            "s1" -> AWG_S1
            "s2" -> AWG_S2
            "s3" -> AWG_S3
            "s4" -> AWG_S4
            "h1" -> AWG_H1
            "h2" -> AWG_H2
            "h3" -> AWG_H3
            "h4" -> AWG_H4
            "i1" -> AWG_I1
            "i2" -> AWG_I2
            "i3" -> AWG_I3
            "i4" -> AWG_I4
            "i5" -> AWG_I5
            "hp_key" -> AWG_HEADER_PROTECTION_KEY
            "cp_addition" -> AWG_CONTENT_PADDING_ADDITION
            "rekey_after" -> AWG_REKEY_AFTER_TIME
            "rekey_timeout" -> AWG_REKEY_TIMEOUT
            "reject_after" -> AWG_REJECT_AFTER_TIME
            "keepalive_timeout" -> AWG_KEEPALIVE_TIMEOUT
            "max_handshake" -> AWG_MAX_HANDSHAKE_ATTEMPTS
            "persistent_keepalive" -> AWG_PERSISTENT_KEEPALIVE
            "junk_level" -> AWG_JUNK_LEVEL
            else -> return
        }

        var changed = false
        dataStore.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            val typedKey = key as Preferences.Key<Any>
            val old = prefs[typedKey]
            if (old != value) {
                prefs[typedKey] = value
                changed = true
            }
        }
        if (changed && keyName != "junk_level") {
            _connectionConfigChanged.tryEmit(Unit)
        }
    }

    suspend fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int, s3: Int = 0, s4: Int = 0,
        h1: String, h2: String, h3: String, h4: String,
        i1: String, i2: String = "", i3: String = "", i4: String = "", i5: String = "",
        headerProtectionKey: String = "",
        contentPaddingAddition: String = "",
        rekeyAfterTime: String = "",
        rekeyTimeout: String = "",
        rejectAfterTime: String = "",
        keepaliveTimeout: String = "",
        maxHandshakeAttempts: String = "",
        persistentKeepalive: String = "",
        junkLevel: Int = 3
    ) {
        var changed = false
        dataStore.edit {
            // junkLevel is a UI-only marker for the selected preset, so it is not compared here.
            fun <T> put(key: Preferences.Key<T>, default: T, value: T) {
                changed = changed || (it[key] ?: default) != value
                it[key] = value
            }
            put(AWG_JC, 3, jc)
            put(AWG_JMIN, 1, jmin)
            put(AWG_JMAX, 3, jmax)
            put(AWG_S1, 0, s1)
            put(AWG_S2, 0, s2)
            put(AWG_S3, 0, s3)
            put(AWG_S4, 0, s4)
            put(AWG_H1, "1", h1)
            put(AWG_H2, "2", h2)
            put(AWG_H3, "3", h3)
            put(AWG_H4, "4", h4)
            put(AWG_I1, DEFAULT_I1, i1)
            put(AWG_I2, "", i2)
            put(AWG_I3, "", i3)
            put(AWG_I4, "", i4)
            put(AWG_I5, "", i5)
            put(AWG_HEADER_PROTECTION_KEY, "", headerProtectionKey)
            put(AWG_CONTENT_PADDING_ADDITION, "", contentPaddingAddition)
            put(AWG_REKEY_AFTER_TIME, "", rekeyAfterTime)
            put(AWG_REKEY_TIMEOUT, "", rekeyTimeout)
            put(AWG_REJECT_AFTER_TIME, "", rejectAfterTime)
            put(AWG_KEEPALIVE_TIMEOUT, "", keepaliveTimeout)
            put(AWG_MAX_HANDSHAKE_ATTEMPTS, "", maxHandshakeAttempts)
            put(AWG_PERSISTENT_KEEPALIVE, "", persistentKeepalive)
            it[AWG_JUNK_LEVEL] = junkLevel
        }
        if (changed) _connectionConfigChanged.tryEmit(Unit)
    }

    suspend fun clearAll() {
        prefs.edit { clear() }
        dataStore.edit { it.clear() }
    }

    suspend fun getAllPreferences(): Map<String, String> {
        val prefs = dataStore.data.first()
        return prefs.asMap().entries.associate { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            val stringValue = when (value) {
                is Set<*> -> Json.encodeToString(value as Set<String>)
                else -> value.toString()
            }
            key.name to stringValue
        }
    }

    suspend fun importPreferences(preferences: Map<String, String>) {
        dataStore.edit { settings ->
            preferences.forEach { (keyName, value) ->
                val key = findKey(keyName) ?: return@forEach

                // Manually handle each key based on its known type
                when (keyName) {
                    KILL_SWITCH.name, AUTO_CONNECT.name, NOTIFICATIONS.name,
                    SPLIT_TUNNELING_ENABLED.name, ST_SHOW_SYSTEM_APPS.name,
                    API_BYPASS_ENABLED.name, SPOOF_COUNTRY_ENABLED.name,
                    SPOOF_COUNTRY_NULL.name, OBFUSCATION_ENABLED.name,
                    OBFUSCATION_ADVANCED_MODE.name, ANALYTICS_ENABLED.name,
                    CRASH_REPORTS_ENABLED.name, SENTRY_PERFORMANCE_ENABLED.name,
                    SENTRY_NON_FATAL_ENABLED.name, SENTRY_SESSION_REPLAY_ENABLED.name,
                    SENTRY_ANR_ENABLED.name, SENTRY_METRICS_ENABLED.name,
                    SENTRY_LOGS_ENABLED.name, RECONNECT_HINT_ENABLED.name,
                    AI_ENABLED.name, IP_HIDDEN.name, IP_ROTATION_ENABLED.name,
                    IP_ROTATION_KEEP_COUNTRY.name -> {
                        val boolValue = value.toBoolean()
                        @Suppress("UNCHECKED_CAST")
                        settings[key as Preferences.Key<Boolean>] = boolValue
                        prefs.edit { putBoolean(keyName, boolValue) }
                    }

                    VPN_PORT.name, API_PROXY_PORT.name, POLICY_ACCEPTED_VERSION.name,
                    AWG_JC.name, AWG_JMIN.name, AWG_JMAX.name, AWG_S1.name, AWG_S2.name,
                    AWG_S3.name, AWG_S4.name, AWG_JUNK_LEVEL.name,
                    IP_ROTATION_INTERVAL_MINUTES.name -> {
                        val intValue = value.toIntOrNull() ?: return@forEach
                        @Suppress("UNCHECKED_CAST")
                        settings[key as Preferences.Key<Int>] = intValue
                        prefs.edit { putInt(keyName, intValue) }
                    }

                    OTA_LAST_CHECK_TIME.name, PAUSE_END_TIME.name -> {
                        val longValue = value.toLongOrNull() ?: return@forEach
                        @Suppress("UNCHECKED_CAST")
                        settings[key as Preferences.Key<Long>] = longValue
                        prefs.edit { putLong(keyName, longValue) }
                    }

                    OTA_UPDATE_FREQUENCY.name, APP_THEME.name,
                    SERVER_LOAD_DISPLAY_MODE.name, SPLIT_TUNNELING_MODE.name, CUSTOM_DNS.name,
                    API_BYPASS_STRATEGY.name, BYEDPI_FLAGS.name, BYEDPI_SNI.name,
                    API_PROXY_HOST.name, API_PROXY_TYPE.name, API_PROXY_USERNAME.name,
                    API_PROXY_PASSWORD.name, SPOOF_COUNTRY_CODE.name, SELECTED_PROFILE_ID.name,
                    CUSTOM_PROFILES.name, NETSHIELD_LEVEL.name, QUICK_CONNECT_STRATEGY.name,
                    QUICK_CONNECT_TARGET_ID.name, SETUP_STEP.name, AI_PROVIDER.name,
                    AI_MODEL.name, AI_API_KEY.name, AWG_H1.name,
                    AWG_H2.name, AWG_H3.name, AWG_H4.name, AWG_I1.name, AWG_I2.name,
                    AWG_I3.name, AWG_I4.name, AWG_I5.name, AWG_HEADER_PROTECTION_KEY.name,
                    AWG_CONTENT_PADDING_ADDITION.name, AWG_REKEY_AFTER_TIME.name,
                    AWG_REKEY_TIMEOUT.name, AWG_REJECT_AFTER_TIME.name,
                    AWG_KEEPALIVE_TIMEOUT.name, AWG_MAX_HANDSHAKE_ATTEMPTS.name,
                    AWG_PERSISTENT_KEEPALIVE.name -> {
                        @Suppress("UNCHECKED_CAST")
                        settings[key as Preferences.Key<String>] = value
                        prefs.edit { putString(keyName, value) }
                    }

                    EXCLUDED_APPS.name, EXCLUDED_IPS.name, EXCLUDED_DOMAINS.name -> {
                        try {
                            val set = Json.decodeFromString<Set<String>>(value)
                            @Suppress("UNCHECKED_CAST")
                            settings[key as Preferences.Key<Set<String>>] = set
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private fun findKey(name: String): Preferences.Key<*>? {
        return when (name) {
            KILL_SWITCH.name -> KILL_SWITCH
            AUTO_CONNECT.name -> AUTO_CONNECT
            NOTIFICATIONS.name -> NOTIFICATIONS
            OTA_UPDATE_FREQUENCY.name -> OTA_UPDATE_FREQUENCY
            OTA_LAST_CHECK_TIME.name -> OTA_LAST_CHECK_TIME
            APP_THEME.name -> APP_THEME
            SERVER_LOAD_DISPLAY_MODE.name -> SERVER_LOAD_DISPLAY_MODE
            SPLIT_TUNNELING_ENABLED.name -> SPLIT_TUNNELING_ENABLED
            SPLIT_TUNNELING_MODE.name -> SPLIT_TUNNELING_MODE
            EXCLUDED_APPS.name -> EXCLUDED_APPS
            EXCLUDED_IPS.name -> EXCLUDED_IPS
            EXCLUDED_DOMAINS.name -> EXCLUDED_DOMAINS
            ST_SHOW_SYSTEM_APPS.name -> ST_SHOW_SYSTEM_APPS
            VPN_PORT.name -> VPN_PORT
            CUSTOM_DNS.name -> CUSTOM_DNS
            API_BYPASS_ENABLED.name -> API_BYPASS_ENABLED
            API_BYPASS_STRATEGY.name -> API_BYPASS_STRATEGY
            BYEDPI_FLAGS.name -> BYEDPI_FLAGS
            BYEDPI_SNI.name -> BYEDPI_SNI
            API_PROXY_HOST.name -> API_PROXY_HOST
            API_PROXY_PORT.name -> API_PROXY_PORT
            API_PROXY_TYPE.name -> API_PROXY_TYPE
            API_PROXY_USERNAME.name -> API_PROXY_USERNAME
            API_PROXY_PASSWORD.name -> API_PROXY_PASSWORD
            SPOOF_COUNTRY_ENABLED.name -> SPOOF_COUNTRY_ENABLED
            SPOOF_COUNTRY_NULL.name -> SPOOF_COUNTRY_NULL
            SPOOF_COUNTRY_CODE.name -> SPOOF_COUNTRY_CODE
            OBFUSCATION_ENABLED.name -> OBFUSCATION_ENABLED
            OBFUSCATION_ADVANCED_MODE.name -> OBFUSCATION_ADVANCED_MODE
            IP_ROTATION_ENABLED.name -> IP_ROTATION_ENABLED
            IP_ROTATION_INTERVAL_MINUTES.name -> IP_ROTATION_INTERVAL_MINUTES
            IP_ROTATION_KEEP_COUNTRY.name -> IP_ROTATION_KEEP_COUNTRY
            SELECTED_PROFILE_ID.name -> SELECTED_PROFILE_ID
            CUSTOM_PROFILES.name -> CUSTOM_PROFILES
            NETSHIELD_LEVEL.name -> NETSHIELD_LEVEL
            ANALYTICS_ENABLED.name -> ANALYTICS_ENABLED
            CRASH_REPORTS_ENABLED.name -> CRASH_REPORTS_ENABLED
            SENTRY_PERFORMANCE_ENABLED.name -> SENTRY_PERFORMANCE_ENABLED
            SENTRY_NON_FATAL_ENABLED.name -> SENTRY_NON_FATAL_ENABLED
            SENTRY_SESSION_REPLAY_ENABLED.name -> SENTRY_SESSION_REPLAY_ENABLED
            SENTRY_ANR_ENABLED.name -> SENTRY_ANR_ENABLED
            SENTRY_METRICS_ENABLED.name -> SENTRY_METRICS_ENABLED
            SENTRY_LOGS_ENABLED.name -> SENTRY_LOGS_ENABLED
            QUICK_CONNECT_STRATEGY.name -> QUICK_CONNECT_STRATEGY
            QUICK_CONNECT_TARGET_ID.name -> QUICK_CONNECT_TARGET_ID
            IP_HIDDEN.name -> IP_HIDDEN
            PAUSE_END_TIME.name -> PAUSE_END_TIME
            POLICY_ACCEPTED_VERSION.name -> POLICY_ACCEPTED_VERSION
            SETUP_STEP.name -> SETUP_STEP
            RECONNECT_HINT_ENABLED.name -> RECONNECT_HINT_ENABLED
            AI_ENABLED.name -> AI_ENABLED
                    AI_PROVIDER.name -> AI_PROVIDER
            AI_MODEL.name -> AI_MODEL
            AI_API_KEY.name -> AI_API_KEY
            AI_BYPASS_BLOCKS.name -> AI_BYPASS_BLOCKS
            AWG_JC.name -> AWG_JC
            AWG_JMIN.name -> AWG_JMIN
            AWG_JMAX.name -> AWG_JMAX
            AWG_S1.name -> AWG_S1
            AWG_S2.name -> AWG_S2
            AWG_S3.name -> AWG_S3
            AWG_S4.name -> AWG_S4
            AWG_H1.name -> AWG_H1
            AWG_H2.name -> AWG_H2
            AWG_H3.name -> AWG_H3
            AWG_H4.name -> AWG_H4
            AWG_I1.name -> AWG_I1
            AWG_I2.name -> AWG_I2
            AWG_I3.name -> AWG_I3
            AWG_I4.name -> AWG_I4
            AWG_I5.name -> AWG_I5
            AWG_HEADER_PROTECTION_KEY.name -> AWG_HEADER_PROTECTION_KEY
            AWG_CONTENT_PADDING_ADDITION.name -> AWG_CONTENT_PADDING_ADDITION
            AWG_REKEY_AFTER_TIME.name -> AWG_REKEY_AFTER_TIME
            AWG_REKEY_TIMEOUT.name -> AWG_REKEY_TIMEOUT
            AWG_REJECT_AFTER_TIME.name -> AWG_REJECT_AFTER_TIME
            AWG_KEEPALIVE_TIMEOUT.name -> AWG_KEEPALIVE_TIMEOUT
            AWG_MAX_HANDSHAKE_ATTEMPTS.name -> AWG_MAX_HANDSHAKE_ATTEMPTS
            AWG_PERSISTENT_KEEPALIVE.name -> AWG_PERSISTENT_KEEPALIVE
            AWG_JUNK_LEVEL.name -> AWG_JUNK_LEVEL
            else -> null
        }
    }
}
