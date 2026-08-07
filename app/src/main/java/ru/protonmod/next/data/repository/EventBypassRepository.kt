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

package ru.protonmod.next.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.model.eventbypass.EVENT_BYPASS_SUPPORTED_VERSION
import ru.protonmod.next.data.model.eventbypass.EventBypassResponse
import ru.protonmod.next.data.network.eventbypass.EventBypassApi
import ru.protonmod.next.utils.NetworkMonitor
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.VpnTunnelState
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

/** Outcome of a single refresh attempt, mapped to a user-facing string by the UI. */
enum class EventBypassResult {
    /** A mirror answered and published a usable endpoint. */
    UPDATED,

    /** A mirror answered, but no bypass is published right now. */
    NOT_CONFIGURED,

    /** The device has no internet at all. */
    BLOCKED_OFFLINE,

    /** A third-party VPN is up; the fetch would leave through someone else's exit. */
    BLOCKED_VPN,

    /** Every mirror failed, timed out or returned something that is not our config. */
    UNREACHABLE
}

/**
 * Fetches the temporary ("event") bypass endpoint from `event-bypass.json`.
 *
 * The mirrors are tried in order and the first valid answer wins. Git forges come
 * first on purpose: they hold the source of truth and stay reachable even while a
 * hosting platform is being migrated, which is exactly when this config changes.
 */
@Singleton
class EventBypassRepository @Inject constructor(
    private val eventBypassApi: EventBypassApi,
    private val settingsManager: SettingsManager,
    private val networkMonitor: NetworkMonitor,
    private val vpnManagerProvider: Provider<AmneziaVpnManager>,
    @ApplicationContext private val context: Context
) {
    private val mirrorUrls = listOf(
        context.getString(R.string.url_event_bypass_1),
        context.getString(R.string.url_event_bypass_2),
        context.getString(R.string.url_event_bypass_3),
        context.getString(R.string.url_event_bypass_4),
        context.getString(R.string.url_event_bypass_5),
        context.getString(R.string.url_event_bypass_6)
    )

    /**
     * A third-party VPN is any VPN transport that is not our own tunnel. Our tunnel
     * is fine to fetch through; someone else's is not, because we would be trusting
     * their exit node with the config that decides where API traffic goes.
     */
    private fun isThirdPartyVpnActive(): Boolean {
        val ourTunnelUp = try {
            vpnManagerProvider.get().tunnelState.value == VpnTunnelState.UP
        } catch (e: Exception) {
            false
        }
        if (ourTunnelUp) return false
        return networkMonitor.isVpnActive.value
    }

    /** True when a refresh would be refused right now, used to disable the UI button. */
    fun blockedReason(): EventBypassResult? = when {
        !networkMonitor.isNetworkAvailable.value -> EventBypassResult.BLOCKED_OFFLINE
        isThirdPartyVpnActive() -> EventBypassResult.BLOCKED_VPN
        else -> null
    }

    private suspend fun fetchConfig(url: String): EventBypassResponse {
        val response = eventBypassApi.getEventBypassConfig(url)
        val body = response.body()
        val contentType = response.headers()["Content-Type"] ?: ""

        if (!response.isSuccessful || body == null) {
            throw HttpException(response)
        }

        // Captive portals and "page not found" stubs answer 200 with HTML. Parsing that
        // as JSON would either throw a confusing error or, worse, succeed on junk.
        if (!contentType.contains("application/json", ignoreCase = true) &&
            !contentType.contains("text/plain", ignoreCase = true)
        ) {
            throw IllegalStateException(
                "Event bypass URL '$url' returned non-JSON content (Content-Type: '$contentType')"
            )
        }

        return json.decodeFromString(EventBypassResponse.serializer(), body.string())
    }

    suspend fun refresh(): EventBypassResult {
        blockedReason()?.let { return it }

        for (url in mirrorUrls) {
            if (url.isBlank()) continue

            val config = try {
                withTimeoutOrNull(10_000) {
                    val urlWithCacheBuster = if (url.contains("?")) {
                        "$url&t=${System.currentTimeMillis()}"
                    } else {
                        "$url?t=${System.currentTimeMillis()}"
                    }
                    fetchConfig(urlWithCacheBuster)
                }
            } catch (e: HttpException) {
                ProtonLogger.w("EventBypassRepository", "HTTP ${e.code()} from $url")
                null
            } catch (e: Exception) {
                ProtonLogger.w("EventBypassRepository", "Failed to fetch event bypass from $url: ${e.message}")
                null
            } ?: continue

            if (config.version > EVENT_BYPASS_SUPPORTED_VERSION) {
                ProtonLogger.w(
                    "EventBypassRepository",
                    "Ignoring event bypass config version ${config.version} from $url"
                )
                continue
            }

            val event = config.event
            val endpoint = event?.normalizedUrl().orEmpty()
            settingsManager.setEventBypass(
                name = event?.name.orEmpty(),
                url = endpoint,
                updatedAt = config.updatedAt
            )
            settingsManager.setEventBypassLastSync(System.currentTimeMillis())

            ProtonLogger.i(
                "EventBypassRepository",
                "Event bypass config loaded from $url (name='${event?.name.orEmpty()}', usable=${endpoint.isNotEmpty()})"
            )

            return if (endpoint.isEmpty()) EventBypassResult.NOT_CONFIGURED else EventBypassResult.UPDATED
        }

        return EventBypassResult.UNREACHABLE
    }
}
