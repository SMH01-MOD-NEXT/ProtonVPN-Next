/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger

@Singleton
class AiManager @Inject constructor(
    private val aiClient: AiClient,
    private val actionExecutor: AiActionExecutor,
    private val settingsManager: SettingsManager,
    private val providerRepository: AiProviderRepository,
) {
    suspend fun processQuery(query: String, currentProposal: AiProposal? = null): AiResult {
        if (!settingsManager.aiEnabled.first()) return AiResult.Error("AI Mode is disabled")
        val apiKey = settingsManager.aiApiKey.first()
        if (apiKey.isBlank()) return AiResult.NoApiKey

        val provider = providerRepository.selected()
        val model = settingsManager.aiModel.first()
        val useBypass = settingsManager.aiBypassBlocks.first()
        val userPrompt = if (currentProposal == null) query else """
            Current proposal: ${currentProposal.asRefinementContext()}
            User requested this change to the proposal: $query
            Return a complete replacement proposal, not a partial patch.
        """.trimIndent()
        val systemPrompt = buildSystemPrompt()

        ProtonLogger.d(TAG, "Requesting AI proposal ($provider, $model, refinement=${currentProposal != null})")
        val response = aiClient.query(provider, model, apiKey, systemPrompt, userPrompt, useBypass)
            ?: return AiResult.Error("Failed to get response from AI")
        return actionExecutor.parseProposal(response)
    }

    suspend fun applyProposal(proposal: AiProposal): AiResult = actionExecutor.executeProposal(proposal)

    private suspend fun buildSystemPrompt(): String {
        val appContext = actionExecutor.getAssistantContext()
        val installedApps = actionExecutor.getInstalledAppsContext()
        return """
            You are the in-app configuration assistant for Proton VPN-Next on Android.
            Convert the user's request into a REVIEWABLE proposal. Never claim an action was already executed.
            Return JSON only in this exact top-level shape:
            {"title":"Short title","summary":"One concise sentence","actions":[{...}]}

            Supported actions:
            - {"action":"create_profile","name":String,"serverId":String?,"city":String?,"country":String?,"port":Int?,"obfuscationEnabled":Boolean?,"obfuscationProfileId":String?,"connectAndGoUrl":String?}
            - {"action":"update_profile","profileId":String?,"profileName":String?, plus any create_profile fields to change}
            - {"action":"delete_profile","profileId":String?,"profileName":String?}
            - {"action":"set_setting","key":String,"value":Any}
            - {"action":"set_obfuscation","enabled":Boolean,"advanced":Boolean?,"profileId":String?,"params":Object?}
            - {"action":"set_awg_params", any AmneziaWG fields: jc,jmin,jmax,s1,s2,s3,s4,h1-h4,i1-i5,headerProtectionKey,contentPaddingAddition,rekeyAfterTime,rekeyTimeout,rejectAfterTime,keepaliveTimeout,maxHandshakeAttempts,persistentKeepalive,junkLevel}
            - {"action":"refresh_servers"} (refreshes both country/server list and loads)
            - {"action":"connect","serverName":String?,"city":String?,"country":String?}
            - {"action":"disconnect"}
            - {"action":"set_split_tunneling","enabled":Boolean,"mode":"exclude"|"include"}
            - {"action":"add_split_tunneling_app","packageName":String}
            - {"action":"remove_split_tunneling_app","packageName":String}
            - {"action":"refresh_certificate"}, {"action":"refresh_session"}, {"action":"set_certificate_type","type":"temporary"|"extended"}

            set_setting keys:
            auto_connect, notifications, kill_switch, allow_lan, reconnect_hint, vpn_port, custom_dns,
            split_tunneling_enabled, split_tunneling_mode, server_load_display, theme, netshield, tor_mode,
            ip_rotation_enabled, ip_rotation_interval (5|15|30|60), ip_rotation_keep_country,
            obfuscation_enabled, obfuscation_advanced, proxy_chain_enabled, proxy_chain_config,
            api_bypass_enabled, api_bypass_strategy, spoof_country_enabled, spoof_country_null,
            spoof_country_code, traffic_stats, connection_verification_mode, handshake_timeout (3..30 seconds),
            connection_verification_required, connection_preflight_required,
            connection_failure_detection, connection_auto_reconnect.

            Rules:
            1. Only manage this app and VPN connections. Refuse unrelated requests by returning a proposal with no unsupported action is NOT allowed; instead use {"action":"set_setting","key":"unsupported","value":"reason"} only when the request is app-related but unavailable.
            2. Use profile IDs from context when editing/deleting. If only a unique profile name exists, profileName is acceptable.
            3. For create_profile and update_profile, always return a complete preview: name, protocol, country, city, serverId, serverName, port, obfuscationEnabled, obfuscationProfileId, obfuscationProfileName, and connectAndGoUrl. Use null for deliberately automatic/disabled values. Never omit these fields.
            4. Use country ISO codes when possible. Port 0 means automatic. If serverId/serverName are null, the profile uses the fastest matching server.
            5. For a request to refresh load or countries, use refresh_servers.
            6. Keep titles and summaries in the user's language.

            Current app context:
            $appContext

            Installed apps:
            $installedApps
        """.trimIndent()
    }

    private companion object { const val TAG = "AiManager" }
}

sealed class AiResult {
    data object Success : AiResult()
    data object NoApiKey : AiResult()
    data class ProposalReady(val proposal: AiProposal) : AiResult()
    data class Error(val message: String) : AiResult()
}
