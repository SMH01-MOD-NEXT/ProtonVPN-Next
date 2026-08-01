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

package ru.protonmod.next.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import ru.protonmod.next.data.local.*
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.vpn.AmneziaVpnManager
import java.util.*

@Singleton
class AiActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val vpnRepository: VpnRepository,
    private val authRepository: AuthRepository,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val sessionDao: SessionDao,
    private val profileDao: ProfileDao
) {
    suspend fun executeActions(jsonString: String): AiResult {
        try {
            val root = if (jsonString.trim().startsWith("[")) {
                JSONArray(jsonString)
            } else {
                JSONArray().put(JSONObject(jsonString))
            }

            for (i in 0 until root.length()) {
                val item = root.getJSONObject(i)
                val action = item.optString("action")
                
                val result = when (action) {
                    "set_vpn_port" -> executeSetPort(item)
                    "set_split_tunneling" -> executeSetSplitTunneling(item)
                    "add_split_tunneling_app" -> executeAddSplitApp(item)
                    "remove_split_tunneling_app" -> executeRemoveSplitApp(item)
                    "set_kill_switch" -> executeSetKillSwitch(item)
                    "set_dns" -> executeSetDns(item)
                    "set_theme" -> executeSetTheme(item)
                    "set_netshield" -> executeSetNetShield(item)
                    "connect" -> executeConnect(item)
                    "refresh_certificate" -> executeRefreshCertificate()
                    "refresh_session" -> executeRefreshSession()
                    "set_certificate_type" -> executeSetCertificateType(item)
                    "create_profile" -> executeCreateProfile(item)
                    else -> AiResult.Error("Unknown action: $action")
                }
                
                if (result is AiResult.Error) return result
            }
            return AiResult.Success
        } catch (e: Exception) {
            ProtonLogger.e("AiActionExecutor", "Failed to execute AI actions", e)
            return AiResult.Error("Failed to parse or execute AI commands")
        }
    }

    private suspend fun executeSetPort(item: JSONObject): AiResult {
        settingsManager.setVpnPort(item.optInt("value", 0))
        return AiResult.Success
    }

    private suspend fun executeSetSplitTunneling(item: JSONObject): AiResult {
        settingsManager.setSplitTunnelingEnabled(item.optBoolean("enabled", false))
        settingsManager.setSplitTunnelingMode(item.optString("mode", "exclude"))
        return AiResult.Success
    }

    private suspend fun executeAddSplitApp(item: JSONObject): AiResult {
        val pkg = item.optString("packageName")
        if (pkg.isNotEmpty()) {
            val current = settingsManager.excludedApps.firstOrNull() ?: emptySet()
            settingsManager.setExcludedApps(current + pkg)
        }
        return AiResult.Success
    }

    private suspend fun executeRemoveSplitApp(item: JSONObject): AiResult {
        val pkg = item.optString("packageName")
        if (pkg.isNotEmpty()) {
            val current = settingsManager.excludedApps.firstOrNull() ?: emptySet()
            settingsManager.setExcludedApps(current - pkg)
        }
        return AiResult.Success
    }

    private suspend fun executeSetKillSwitch(item: JSONObject): AiResult {
        settingsManager.setKillSwitch(item.optBoolean("enabled", false))
        return AiResult.Success
    }

    private suspend fun executeSetDns(item: JSONObject): AiResult {
        settingsManager.setCustomDns(item.optString("value", ""))
        return AiResult.Success
    }

    private suspend fun executeSetTheme(item: JSONObject): AiResult {
        val themeStr = item.optString("value").uppercase()
        runCatching {
            val theme = AppTheme.valueOf(themeStr)
            settingsManager.setAppTheme(theme)
        }
        return AiResult.Success
    }

    private suspend fun executeSetNetShield(item: JSONObject): AiResult {
        val levelStr = item.optString("value").uppercase()
        runCatching {
            val level = NetShieldLevel.valueOf(levelStr)
            settingsManager.setNetShieldLevel(level)
        }
        return AiResult.Success
    }

    private suspend fun executeConnect(item: JSONObject): AiResult {
        val serverName = item.optString("serverName").trim()
        val city = item.optString("city").trim()
        val country = item.optString("country").trim()

        val allServers = vpnRepository.getCachedServers()
        if (allServers.isEmpty()) return AiResult.Error("Server list is empty. Please refresh first.")

        val targetServer = when {
            serverName.isNotEmpty() -> allServers.find { it.name.equals(serverName, true) || it.id.equals(serverName, true) }
            city.isNotEmpty() -> allServers.filter { it.city.equals(city, true) || it.localizedCity?.equals(city, true) == true }
                .minByOrNull { it.averageLoad }
            country.isNotEmpty() -> allServers.filter { it.exitCountry.equals(country, true) || it.exitCountry.equals(getCountryCode(country), true) }
                .minByOrNull { it.averageLoad }
            else -> null
        }

        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")

        return if (targetServer != null) {
            val physicalServer = targetServer.servers.minByOrNull { it.load } ?: return AiResult.Error("No physical servers available for this location")
            amneziaVpnManager.connect(targetServer.id, physicalServer, session, logicalServer = targetServer)
            AiResult.Success
        } else {
            val missing = when {
                serverName.isNotEmpty() -> "Server \"$serverName\""
                city.isNotEmpty() -> "City \"$city\""
                country.isNotEmpty() -> "Country \"$country\""
                else -> "Target"
            }
            AiResult.Error("$missing not found in available server list.")
        }
    }

    private suspend fun executeRefreshCertificate(): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        val result = vpnRepository.forceRefreshCertificate(session.accessToken, session.sessionId)
        return if (result.isSuccess) AiResult.Success else AiResult.Error("Failed to refresh certificate")
    }

    private suspend fun executeRefreshSession(): AiResult {
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        val result = authRepository.refreshSession(session.sessionId, session.refreshToken)
        return if (result.isSuccess) AiResult.Success else AiResult.Error("Failed to refresh session")
    }

    private suspend fun executeSetCertificateType(item: JSONObject): AiResult {
        val extended = item.optString("type") == "extended"
        val session = sessionDao.getSession() ?: return AiResult.Error("No active session")
        val result = vpnRepository.setExtendedCertEnabled(extended, session.accessToken, session.sessionId)
        return if (result.isSuccess) AiResult.Success else AiResult.Error("Failed to change certificate type")
    }

    private suspend fun executeCreateProfile(item: JSONObject): AiResult {
        val name = item.optString("name").ifBlank { "AI Profile ${System.currentTimeMillis() % 1000}" }
        val serverId = item.optString("serverId")
        val city = item.optString("city")
        val country = item.optString("country")
        val port = item.optInt("port", 0)
        val obfuscation = item.optBoolean("obfuscationEnabled", false)
        val url = item.optString("connectAndGoUrl")

        val profile = VpnProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "AmneziaWG",
            targetServerId = serverId.ifBlank { null },
            targetCity = city.ifBlank { null },
            targetCountry = if (country.isNotBlank()) getCountryCode(country) else null,
            port = port,
            isObfuscationEnabled = obfuscation,
            obfuscationProfileId = if (obfuscation) "standard_1" else null,
            autoOpenUrl = url.ifBlank { null }
        )
        profileDao.insertProfile(profile)
        return AiResult.Success
    }

    private fun getCountryCode(countryName: String): String {
        val map = mapOf(
            "NETHERLANDS" to "NL", "НИДЕРЛАНДЫ" to "NL", "HOLLAND" to "NL",
            "USA" to "US", "США" to "US", "AMERICA" to "US",
            "GERMANY" to "DE", "ГЕРМАНИЯ" to "DE",
            "JAPAN" to "JP", "ЯПОНИЯ" to "JP",
            "SWITZERLAND" to "CH", "ШВЕЙЦАРИЯ" to "CH",
            "UK" to "GB", "UNITED KINGDOM" to "GB", "ВЕЛИКОБРИТАНИЯ" to "GB",
            "FRANCE" to "FR", "ФРАНЦИЯ" to "FR",
            "RUSSIA" to "RU", "РОССИЯ" to "RU",
            "UKRAINE" to "UA", "УКРАИНА" to "UA"
        )
        return map[countryName.uppercase()] ?: countryName.uppercase()
    }

    // Helper to get all installed apps for AI context
    fun getInstalledAppsContext(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        return apps.joinToString(", ") { "${it.loadLabel(pm)} (${it.packageName})" }
    }
    
    private suspend fun <T> Flow<T>.firstOrNull(): T? {
        return try {
            this.first()
        } catch (e: Exception) {
            null
        }
    }
}
