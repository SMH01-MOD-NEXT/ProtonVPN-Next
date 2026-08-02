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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.protonmod.next.data.local.SettingsManager

/**
 * Single source of truth for the provider list: the built-in providers plus every provider the
 * user added. Everything downstream ([AiClient], [AiManager], settings UI) works with
 * [AiProviderConfig] so custom providers are first-class.
 */
@Singleton
class AiProviderRepository @Inject constructor(
    private val settingsManager: SettingsManager,
) {
    val customProviders: Flow<List<AiProviderConfig>> =
        settingsManager.aiCustomProviders.map(AiCustomProviders::decode)

    val providers: Flow<List<AiProviderConfig>> =
        customProviders.map { custom -> AiProvider.builtInConfigs + custom }

    suspend fun all(): List<AiProviderConfig> = providers.first()

    /** Falls back to the default built-in provider when an id no longer exists. */
    suspend fun resolve(id: String): AiProviderConfig =
        all().firstOrNull { it.id == id } ?: AiProvider.OPENAI.toConfig()

    suspend fun selected(): AiProviderConfig = resolve(settingsManager.aiProvider.first())

    /**
     * Adds a new custom provider or updates an existing one.
     *
     * @return the stored provider, whose id is generated on first save.
     */
    suspend fun save(
        id: String?,
        displayName: String,
        baseUrl: String,
        format: AiApiFormat,
        models: List<String> = emptyList(),
    ): AiProviderConfig {
        val name = displayName.trim()
        val url = baseUrl.trim()
        require(name.isNotEmpty()) { "Provider name must not be empty" }
        require(AiEndpoints.isValidBaseUrl(url)) { "Provider base URL must be an absolute http(s) URL" }

        val existing = customProviders.first()
        val reservedIds = (AiProvider.entries.map(AiProvider::id) + existing.map(AiProviderConfig::id)).toSet()
        val provider = AiProviderConfig(
            id = id?.takeIf { current -> existing.any { it.id == current } }
                ?: AiCustomProviders.newId(name, reservedIds),
            displayName = name,
            baseUrl = url,
            format = format,
            models = models.map(String::trim).filter(String::isNotEmpty).distinct(),
            isCustom = true,
        )
        val updated = if (existing.any { it.id == provider.id }) {
            existing.map { if (it.id == provider.id) provider else it }
        } else {
            existing + provider
        }
        settingsManager.setAiCustomProviders(AiCustomProviders.encode(updated))
        return provider
    }

    /** Caches models discovered from the provider API so they can be picked offline later. */
    suspend fun cacheModels(id: String, models: List<String>) {
        val existing = customProviders.first()
        if (existing.none { it.id == id }) return
        val updated = existing.map { if (it.id == id) it.copy(models = models) else it }
        settingsManager.setAiCustomProviders(AiCustomProviders.encode(updated))
    }

    suspend fun delete(id: String) {
        val remaining = customProviders.first().filterNot { it.id == id }
        settingsManager.setAiCustomProviders(AiCustomProviders.encode(remaining))
        if (settingsManager.aiProvider.first() == id) {
            val fallback = AiProvider.OPENAI
            settingsManager.setAiProvider(fallback.id)
            settingsManager.setAiModel(fallback.getDefaultModel())
        }
    }
}
