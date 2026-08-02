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

package ru.protonmod.next.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.protonmod.next.data.ai.AiApiFormat
import ru.protonmod.next.data.ai.AiClient
import ru.protonmod.next.data.ai.AiEndpoints
import ru.protonmod.next.data.ai.AiProvider
import ru.protonmod.next.data.ai.AiProviderConfig
import ru.protonmod.next.data.ai.AiProviderRepository
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

/** UI-level outcome of a model discovery request; mapped to strings by the screen. */
enum class AiModelsStatus { IDLE, LOADING, NO_API_KEY, FAILED, LOADED }

/** Validation outcome of the custom provider form; mapped to strings by the screen. */
enum class AiProviderFormError { NAME_REQUIRED, INVALID_URL }

data class AiSettingsUiState(
    val isAiEnabled: Boolean = false,
    val providers: List<AiProviderConfig> = AiProvider.builtInConfigs,
    val selectedProvider: AiProviderConfig = AiProvider.OPENAI.toConfig(),
    val selectedModel: String = "",
    val apiKey: String = "",
    val aiBypassBlocks: Boolean = true,
    val discoveredModels: List<String> = emptyList(),
    val modelsStatus: AiModelsStatus = AiModelsStatus.IDLE,
    val formError: AiProviderFormError? = null,
) {
    /** Models offered in the picker: what the provider declares plus what the API reported. */
    val availableModels: List<String>
        get() = (selectedProvider.models + discoveredModels).distinct()
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val providerRepository: AiProviderRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    private val discoveredModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val modelsStatus = MutableStateFlow(AiModelsStatus.IDLE)
    private val formError = MutableStateFlow<AiProviderFormError?>(null)

    private val settings = combine(
        settingsManager.aiEnabled,
        settingsManager.aiProvider,
        settingsManager.aiModel,
        settingsManager.aiApiKey,
        settingsManager.aiBypassBlocks,
    ) { enabled, providerId, model, apiKey, bypass ->
        Settings(enabled, providerId, model, apiKey, bypass)
    }

    private val transient = combine(discoveredModels, modelsStatus, formError, ::Triple)

    val uiState: StateFlow<AiSettingsUiState> = combine(
        settings,
        providerRepository.providers,
        transient,
    ) { current, providers, (discovered, status, error) ->
        val provider = providers.firstOrNull { it.id == current.providerId } ?: AiProvider.OPENAI.toConfig()
        AiSettingsUiState(
            isAiEnabled = current.enabled,
            providers = providers,
            selectedProvider = provider,
            selectedModel = current.model.ifBlank { provider.getDefaultModel() },
            apiKey = current.apiKey,
            aiBypassBlocks = current.bypass,
            discoveredModels = discovered[provider.id].orEmpty(),
            modelsStatus = status,
            formError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettingsUiState())

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setAiEnabled(enabled) }
    }

    fun setProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            settingsManager.setAiProvider(provider.id)
            // Keep a usable model: prefer the provider's own default, otherwise let the user pick.
            settingsManager.setAiModel(provider.getDefaultModel())
            modelsStatus.value = AiModelsStatus.IDLE
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch { settingsManager.setAiModel(model.trim()) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { settingsManager.setAiApiKey(key) }
    }

    fun setAiBypassBlocks(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setAiBypassBlocks(enabled) }
    }

    /** Asks the provider for its model catalogue, the way IDEs populate model pickers. */
    fun refreshModels() {
        val state = uiState.value
        if (state.apiKey.isBlank()) {
            modelsStatus.value = AiModelsStatus.NO_API_KEY
            return
        }
        if (modelsStatus.value == AiModelsStatus.LOADING) return
        modelsStatus.value = AiModelsStatus.LOADING
        viewModelScope.launch {
            val provider = state.selectedProvider
            val models = aiClient.listModels(provider, state.apiKey, state.aiBypassBlocks)
            if (models.isNullOrEmpty()) {
                modelsStatus.value = AiModelsStatus.FAILED
                return@launch
            }
            discoveredModels.update { it + (provider.id to models) }
            if (provider.isCustom) providerRepository.cacheModels(provider.id, models)
            modelsStatus.value = AiModelsStatus.LOADED
        }
    }

    /**
     * Creates or updates a user-defined provider and selects it.
     *
     * @param id existing provider id when editing, null when adding.
     */
    fun saveCustomProvider(id: String?, name: String, baseUrl: String, format: AiApiFormat) {
        if (name.isBlank()) {
            formError.value = AiProviderFormError.NAME_REQUIRED
            return
        }
        if (!AiEndpoints.isValidBaseUrl(baseUrl)) {
            formError.value = AiProviderFormError.INVALID_URL
            return
        }
        formError.value = null
        viewModelScope.launch {
            val existingModels = providerRepository.all().firstOrNull { it.id == id }?.models.orEmpty()
            val saved = providerRepository.save(id, name, baseUrl, format, existingModels)
            settingsManager.setAiProvider(saved.id)
            if (saved.models.none { it == settingsManager.aiModel.first() }) {
                settingsManager.setAiModel(saved.getDefaultModel())
            }
            modelsStatus.value = AiModelsStatus.IDLE
        }
    }

    fun deleteCustomProvider(id: String) {
        viewModelScope.launch {
            providerRepository.delete(id)
            discoveredModels.update { it - id }
            modelsStatus.value = AiModelsStatus.IDLE
        }
    }

    fun clearFormError() {
        formError.value = null
    }

    private data class Settings(
        val enabled: Boolean,
        val providerId: String,
        val model: String,
        val apiKey: String,
        val bypass: Boolean,
    )
}
