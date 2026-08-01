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

import kotlinx.coroutines.flow.first
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiManager @Inject constructor(
    private val aiClient: AiClient,
    private val actionExecutor: AiActionExecutor,
    private val settingsManager: SettingsManager
) {
    suspend fun processQuery(query: String): AiResult {
        val enabled = settingsManager.aiEnabled.first()
        if (!enabled) return AiResult.Error("AI Mode is disabled")

        val providerId = settingsManager.aiProvider.first()
        val provider = AiProvider.fromId(providerId)
        val model = settingsManager.aiModel.first()
        val apiKey = settingsManager.aiApiKey.first()
        val useBypass = settingsManager.aiBypassBlocks.first()

        if (apiKey.isBlank()) return AiResult.NoApiKey

        val systemPrompt = buildSystemPrompt()
        
        ProtonLogger.d("AiManager", "Querying AI ($providerId, $model, bypass=$useBypass) with query: $query")
        val response = aiClient.query(provider, model, apiKey, systemPrompt, query, useBypass)
        
        if (response == null) return AiResult.Error("Failed to get response from AI")

        ProtonLogger.d("AiManager", "AI Response: $response")
        return actionExecutor.executeActions(response)
    }

    private fun buildSystemPrompt(): String {
        val apps = actionExecutor.getInstalledAppsContext()
        return """
            You are a helpful assistant for the Proton VPN-Next Android app. 
            Your task is to help users configure the app settings and manage connections.
            
            Strictly follow these rules:
            1. ONLY perform app configuration and VPN management tasks.
            2. If the user asks for something unrelated (e.g. coding, general questions, Flappy Bird), politely refuse.
            3. Return your response ONLY as a JSON object or an array of JSON objects. No conversational text.
            4. Available actions:
               - {"action": "set_vpn_port", "value": Int} (0 for Auto)
               - {"action": "set_split_tunneling", "enabled": Boolean, "mode": "exclude" | "include"}
               - {"action": "add_split_tunneling_app", "packageName": String}
               - {"action": "remove_split_tunneling_app", "packageName": String}
               - {"action": "set_kill_switch", "enabled": Boolean}
               - {"action": "set_dns", "value": String}
               - {"action": "set_theme", "value": "SYSTEM" | "LIGHT" | "DARK" | "AMOLED"}
               - {"action": "set_netshield", "value": "DISABLED" | "MALWARE" | "EXTENDED" | "ADULT"}
               - {"action": "connect", "serverName": String?, "city": String?, "country": String?}
               - {"action": "refresh_certificate"}
               - {"action": "refresh_session"}
               - {"action": "set_certificate_type", "type": "temporary" | "extended"}
               - {"action": "create_profile", "name": String?, "serverId": String?, "city": String?, "country": String?, "port": Int?, "obfuscationEnabled": Boolean?, "connectAndGoUrl": String?}
            
            Instructions for actions:
            - "connect": Specify ONE of serverName (e.g. "NL-FREE#111"), city ("Amsterdam"), or country ("Netherlands").
            - "create_profile": If name is not provided, generate a creative name based on the settings (e.g. "Fast Amsterdam").
            
            Installed apps (Name and Package):
            $apps
            
            Common Countries: Netherlands (NL), USA (US), Japan (JP), Germany (DE), Switzerland (CH).
            
            Example response for "Connect to Amsterdam":
            {"action": "connect", "city": "Amsterdam"}
            
            Example response for "Create profile with name Work, server in USA, port 443":
            {"action": "create_profile", "name": "Work", "country": "USA", "port": 443}
        """.trimIndent()
    }
}

sealed class AiResult {
    object Success : AiResult()
    object NoApiKey : AiResult()
    data class Error(val message: String) : AiResult()
}
