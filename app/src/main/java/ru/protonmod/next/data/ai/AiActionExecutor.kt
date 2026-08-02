/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.AmneziaVpnManager

@Singleton
class AiActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val sessionDao: SessionDao,
    private val profileDao: ProfileDao,
) {
    fun parseProposal(jsonString: String): AiResult = try {
        AiResult.ProposalReady(AiProposalParser.parse(jsonString))
    } catch (error: Exception) {
        ProtonLogger.e(TAG, "Failed to parse AI proposal", error)
        AiResult.Error(error.message ?: "Failed to parse AI response")
    }

    suspend fun executeProposal(proposal: AiProposal): AiResult = try {
        proposal.actions.forEach { proposed ->
            val result = executeAction(JSONObject(proposed.payload))
            if (result is AiResult.Error) return result
        }
        AiResult.Success
    } catch (error: Exception) {
        ProtonLogger.e(TAG, "Failed to execute AI proposal", error)
        AiResult.Error(error.message ?: "Failed to apply AI changes")
    }

    suspend fun executeActions(jsonString: String): AiResult = when (val parsed = parseProposal(jsonString)) {
        is AiResult.ProposalReady -> executeProposal(parsed.proposal)
        else -> parsed
    }

    private suspend fun executeAction(item: JSONObject): AiResult = when (val action = item.optString("action")) {
        "set_vpn_port" -> success { settingsManager.setVpnPort(item.optInt("value", 0)) }
        "set_split_tunneling" -> success {
            settingsManager.setSplitTunnelingEnabled(item.optBoolean("enabled"))
            settingsManager.setSplitTunnelingMode(item.optString("mode", "exclude"))
        }
        "add_split_tunneling_app" -> success {
            val pkg = item.requireText("packageName")
            settingsManager.setExcludedApps(settingsManager.excludedApps.first() + pkg)
        }
        "remove_split_tunneling_app" -> success {
            val pkg = item.requireText("packageName")
            settingsManager.setExcludedApps(settingsManager.excludedApps.first() - pkg)
        }
        "set_kill_switch" -> success { settingsManager.setKillSwitch(item.optBoolean("enabled")) }
        "set_dns" -> success { settingsManager.setCustomDns(item.optString("value")) }
        "set_theme" -> setTheme(item.optString("value"))
        "set_netshield" -> setNetShield(item.optString("value"))
        "set_setting" -> executeSetSetting(item)
        "set_obfuscation" -> executeSetObfuscation(item)
        "set_awg_params" -> executeSetAwgParams(item)
        "connect" -> executeConnect(item)
        "disconnect" -> success { amneziaVpnManager.disconnect() }
        "refresh_servers" -> executeRefreshServers()
        "refresh_certificate" -> executeRefreshCertificate()
        "refresh_session" -> executeRefreshSession()
        "set_certificate_type" -> executeSetCertificateType(item)
        "create_profile" -> executeCreateProfile(item)
        "update_profile" -> executeUpdateProfile(item)
        "delete_profile" -> executeDeleteProfile(item)
        else -> AiResult.Error("Unknown action: $action")
    }

    private suspend fun executeSetSetting(item: JSONObject): AiResult {
        val key = item.optString("key").lowercase()
        val value = item.opt("value")
        return when (key) {
            "auto_connect" -> success { settingsManager.setAutoConnect(value.asBoolean()) }
            "notifications" -> success { settingsManager.setNotifications(value.asBoolean()) }
            "kill_switch" -> success { settingsManager.setKillSwitch(value.asBoolean()) }
            "allow_lan" -> success { settingsManager.setAllowLanEnabled(value.asBoolean()) }
            "reconnect_hint" -> success { settingsManager.setReconnectHintEnabled(value.asBoolean()) }
            "vpn_port" -> success { settingsManager.setVpnPort(value.asInt()) }
            "custom_dns" -> success { settingsManager.setCustomDns(value.asText()) }
            "split_tunneling_enabled" -> success { settingsManager.setSplitTunnelingEnabled(value.asBoolean()) }
            "split_tunneling_mode" -> success { settingsManager.setSplitTunnelingMode(value.asText()) }
            "server_load_display" -> success { settingsManager.setServerLoadDisplayMode(ServerLoadDisplayMode.valueOf(value.asText().uppercase())) }
            "theme" -> setTheme(value.asText())
            "netshield" -> setNetShield(value.asText())
            "tor_mode" -> success { settingsManager.setTorModeEnabled(value.asBoolean()) }
            "ip_rotation_enabled" -> success { settingsManager.setIpRotationEnabled(value.asBoolean()) }
            "ip_rotation_interval" -> success { settingsManager.setIpRotationIntervalMinutes(value.asInt()) }
            "ip_rotation_keep_country" -> success { settingsManager.setIpRotationKeepCountry(value.asBoolean()) }
            "obfuscation_enabled" -> success { settingsManager.setObfuscationEnabled(value.asBoolean()) }
            "obfuscation_advanced" -> success { settingsManager.setObfuscationAdvancedMode(value.asBoolean()) }
            "proxy_chain_enabled" -> success { settingsManager.setProxyChainEnabled(value.asBoolean()) }
            "proxy_chain_config" -> success { settingsManager.setProxyChainConfig(value.asText()) }
            "api_bypass_enabled" -> success { settingsManager.setApiBypassEnabled(value.asBoolean()) }
            "api_bypass_strategy" -> success { settingsManager.setApiBypassStrategy(value.asText()) }
            "spoof_country_enabled" -> success { settingsManager.setSpoofCountryEnabled(value.asBoolean()) }
            "spoof_country_null" -> success { settingsManager.setSpoofCountryNull(value.asBoolean()) }
            "spoof_country_code" -> success { settingsManager.setSpoofCountryCode(value.asText().uppercase()) }
            "traffic_stats" -> success { settingsManager.setTrafficStatsEnabled(value.asBoolean()) }
            "connection_verification_mode" -> success {
                settingsManager.setConnectionVerificationMode(ConnectionVerificationMode.valueOf(value.asText().uppercase()))
            }
            "connection_verification_required" -> success { settingsManager.setConnectionVerificationRequired(value.asBoolean()) }
            "connection_preflight_required" -> success { settingsManager.setConnectionPreflightRequired(value.asBoolean()) }
            "connection_failure_detection" -> success { settingsManager.setConnectionFailureDetection(value.asBoolean()) }
            "connection_auto_reconnect" -> success { settingsManager.setConnectionAutoReconnect(value.asBoolean()) }
            else -> AiResult.Error("Unsupported setting: $key")
        }
    }

    private suspend fun executeSetObfuscation(item: JSONObject): AiResult = success {
        val enabled = item.optBoolean("enabled")
        settingsManager.setObfuscationEnabled(enabled)
        if (item.has("advanced")) settingsManager.setObfuscationAdvancedMode(item.optBoolean("advanced"))
        item.optString("profileId").takeIf(String::isNotBlank)?.let { profileId ->
            settingsManager.setSelectedProfileId(profileId)
        }
        if (item.has("params")) executeSetAwgParams(item.getJSONObject("params")).throwIfError()
    }

    private suspend fun executeSetAwgParams(item: JSONObject): AiResult = success {
        settingsManager.setAwgParams(
            jc = item.optInt("jc", settingsManager.awgJc.first()),
            jmin = item.optInt("jmin", settingsManager.awgJmin.first()),
            jmax = item.optInt("jmax", settingsManager.awgJmax.first()),
            s1 = item.optInt("s1", settingsManager.awgS1.first()),
            s2 = item.optInt("s2", settingsManager.awgS2.first()),
            s3 = item.optInt("s3", settingsManager.awgS3.first()),
            s4 = item.optInt("s4", settingsManager.awgS4.first()),
            h1 = item.optString("h1", settingsManager.awgH1.first()),
            h2 = item.optString("h2", settingsManager.awgH2.first()),
            h3 = item.optString("h3", settingsManager.awgH3.first()),
            h4 = item.optString("h4", settingsManager.awgH4.first()),
            i1 = item.optString("i1", settingsManager.awgI1.first()),
            i2 = item.optString("i2", settingsManager.awgI2.first()),
            i3 = item.optString("i3", settingsManager.awgI3.first()),
            i4 = item.optString("i4", settingsManager.awgI4.first()),
            i5 = item.optString("i5", settingsManager.awgI5.first()),
            headerProtectionKey = item.optString("headerProtectionKey", settingsManager.awgHeaderProtectionKey.first()),
            contentPaddingAddition = item.optString("contentPaddingAddition", settingsManager.awgContentPaddingAddition.first()),
            rekeyAfterTime = item.optString("rekeyAfterTime", settingsManager.awgRekeyAfterTime.first()),
            rekeyTimeout = item.optString("rekeyTimeout", settingsManager.awgRekeyTimeout.first()),
            rejectAfterTime = item.optString("rejectAfterTime", settingsManager.awgRejectAfterTime.first()),
            keepaliveTimeout = item.optString("keepaliveTimeout", settingsManager.awgKeepaliveTimeout.first()),
            maxHandshakeAttempts = item.optString("maxHandshakeAttempts", settingsManager.awgMaxHandshakeAttempts.first()),
            persistentKeepalive = item.optString("persistentKeepalive", settingsManager.awgPersistentKeepalive.first()),
            junkLevel = item.optInt("junkLevel", settingsManager.awgJunkLevel.first()),
        )
    }

    private suspend fun setTheme(value: String): AiResult = runCatching {
        settingsManager.setAppTheme(AppTheme.valueOf(value.uppercase()))
        AiResult.Success
    }.getOrElse { AiResult.Error("Unknown theme: $value") }

    private suspend fun setNetShield(value: String): AiResult = runCatching {
        settingsManager.setNetShieldLevel(NetShieldLevel.valueOf(value.uppercase()))
        AiResult.Success
    }.getOrElse { AiResult.Error("Unknown NetShield level: $value") }

    private suspend fun executeConnect(item: JSONObject): AiResult {
        val allServers = vpnRepository.getCachedServers()
        if (allServers.isEmpty()) return AiResult.Error("Server list is empty. Refresh it first.")
        val serverName = item.optString("serverName").trim()
        val city = item.optString("city").trim()
        val country = item.optString("country").trim()
        val target = when {
            serverName.isNotEmpty() -> allServers.find { it.name.equals(serverName, true) || it.id.equals(serverName, true) }
            city.isNotEmpty() -> allServers.filter { it.city.equals(city, true) || it.localizedCity?.equals(city, true) == true }.minByOrNull { it.averageLoad }
            country.isNotEmpty() -> allServers.filter { it.exitCountry.equals(countryCode(country), true) }.minByOrNull { it.averageLoad }
            else -> allServers.minByOrNull { it.averageLoad }
        } ?: return AiResult.Error("Requested VPN location was not found")
        val physical = target.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: target.servers.minByOrNull { it.load }
            ?: return AiResult.Error("No physical server is available")
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        amneziaVpnManager.connect(target.id, physical, session, logicalServer = target)
        return AiResult.Success
    }

    private suspend fun executeRefreshServers(): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        val result = vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier, forceRefresh = true)
        return if (result.isSuccess) AiResult.Success else AiResult.Error("Failed to refresh countries and server loads")
    }

    private suspend fun executeRefreshCertificate(): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        return if (vpnRepository.forceRefreshCertificate(session.accessToken, session.sessionId).isSuccess) AiResult.Success
        else AiResult.Error("Failed to refresh certificate")
    }

    private suspend fun executeRefreshSession(): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        return if (authRepository.refreshSession(session.sessionId, session.refreshToken).isSuccess) AiResult.Success
        else AiResult.Error("Failed to refresh session")
    }

    private suspend fun executeSetCertificateType(item: JSONObject): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        val extended = item.optString("type") == "extended"
        return if (vpnRepository.setExtendedCertEnabled(extended, session.accessToken, session.sessionId).isSuccess) AiResult.Success
        else AiResult.Error("Failed to change certificate type")
    }

    private suspend fun executeCreateProfile(item: JSONObject): AiResult {
        val entity = profileFromJson(item, UUID.randomUUID().toString(), System.currentTimeMillis())
        profileDao.insertProfile(entity)
        return AiResult.Success
    }

    private suspend fun executeUpdateProfile(item: JSONObject): AiResult {
        val existing = findProfile(item) ?: return AiResult.Error("Profile not found")
        profileDao.insertProfile(profileFromJson(item, existing.id, existing.createdAt, existing))
        return AiResult.Success
    }

    private suspend fun executeDeleteProfile(item: JSONObject): AiResult {
        val existing = findProfile(item) ?: return AiResult.Error("Profile not found")
        profileDao.deleteProfileById(existing.id)
        return AiResult.Success
    }

    private suspend fun findProfile(item: JSONObject): VpnProfileEntity? {
        item.optString("profileId").takeIf(String::isNotBlank)?.let { id -> profileDao.getProfileById(id)?.let { return it } }
        val name = item.optString("profileName").ifBlank { item.optString("name") }
        return profileDao.getAllProfiles().singleOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun profileFromJson(
        item: JSONObject,
        id: String,
        createdAt: Long,
        existing: VpnProfileEntity? = null,
    ) = VpnProfileEntity(
        id = id,
        name = item.stringOrExisting("name", existing?.name).ifBlank { "AI Profile" },
        protocol = item.stringOrExisting("protocol", existing?.protocol).ifBlank { "AmneziaWG" },
        port = if (item.has("port")) item.optInt("port") else existing?.port ?: 0,
        isObfuscationEnabled = if (item.has("obfuscationEnabled")) item.optBoolean("obfuscationEnabled") else existing?.isObfuscationEnabled ?: false,
        obfuscationProfileId = item.nullableString("obfuscationProfileId", existing?.obfuscationProfileId),
        autoOpenUrl = item.nullableString("connectAndGoUrl", existing?.autoOpenUrl),
        targetServerId = item.nullableString("serverId", existing?.targetServerId),
        targetCountry = item.optString("country").takeIf(String::isNotBlank)?.let(::countryCode) ?: existing?.targetCountry,
        targetCity = item.nullableString("city", existing?.targetCity),
        createdAt = createdAt,
    )

    suspend fun getAssistantContext(): String {
        val profiles = profileDao.getAllProfiles().joinToString("; ") {
            "${it.name}[id=${it.id}, country=${it.targetCountry}, city=${it.targetCity}, server=${it.targetServerId}, port=${it.port}, obfuscation=${it.isObfuscationEnabled}]"
        }.ifBlank { "none" }
        val servers = vpnRepository.getCachedServers()
        val countries = servers.groupBy { it.exitCountry }.entries.sortedBy { it.key }.joinToString(", ") { (code, list) ->
            "$code(${list.size}, avgLoad=${list.map { it.averageLoad }.average().toInt()})"
        }.ifBlank { "none" }
        return """
            Profiles: $profiles
            Countries and current loads: $countries
            Current settings: port=${settingsManager.vpnPort.first()}, DNS=${settingsManager.customDns.first().ifBlank { "default" }}, theme=${settingsManager.appTheme.first()}, NetShield=${settingsManager.netShieldLevel.first()}, Tor=${settingsManager.torModeEnabled.first()}, obfuscation=${settingsManager.obfuscationEnabled.first()}, IP rotation=${settingsManager.ipRotationEnabled.first()} every ${settingsManager.ipRotationIntervalMinutes.first()} min.
        """.trimIndent()
    }

    fun getInstalledAppsContext(): String = context.packageManager.getInstalledApplications(0)
        .joinToString(", ") { "${it.loadLabel(context.packageManager)} (${it.packageName})" }

    private suspend inline fun success(block: () -> Unit): AiResult = try {
        block()
        AiResult.Success
    } catch (error: Exception) {
        AiResult.Error(error.message ?: "Action failed")
    }

    private fun AiResult.throwIfError() {
        if (this is AiResult.Error) throw IllegalArgumentException(message)
    }

    private fun JSONObject.requireText(key: String): String = optString(key).takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("$key is required")

    private fun JSONObject.stringOrExisting(key: String, existing: String?): String =
        if (has(key)) optString(key) else existing.orEmpty()

    private fun JSONObject.nullableString(key: String, existing: String?): String? =
        if (has(key)) optString(key).trim().takeIf(String::isNotEmpty) else existing

    private fun Any?.asBoolean(): Boolean = when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        else -> toString().equals("true", true) || toString() == "1"
    }

    private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: toString().toInt()
    private fun Any?.asText(): String = if (this == null || this == JSONObject.NULL) "" else toString()

    private fun countryCode(countryName: String): String = mapOf(
        "NETHERLANDS" to "NL", "НИДЕРЛАНДЫ" to "NL", "HOLLAND" to "NL",
        "USA" to "US", "США" to "US", "AMERICA" to "US",
        "GERMANY" to "DE", "ГЕРМАНИЯ" to "DE", "JAPAN" to "JP", "ЯПОНИЯ" to "JP",
        "SWITZERLAND" to "CH", "ШВЕЙЦАРИЯ" to "CH", "UK" to "GB",
        "UNITED KINGDOM" to "GB", "ВЕЛИКОБРИТАНИЯ" to "GB", "FRANCE" to "FR",
        "ФРАНЦИЯ" to "FR", "RUSSIA" to "RU", "РОССИЯ" to "RU", "UKRAINE" to "UA", "УКРАИНА" to "UA",
    )[countryName.uppercase()] ?: countryName.uppercase()

    private companion object { const val TAG = "AiActionExecutor" }
}
