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
import ru.protonmod.next.data.ai.AiProvider
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

data class AiSettingsUiState(
    val isAiEnabled: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.OPENAI,
    val selectedModel: String = "",
    val apiKey: String = "",
    val aiBypassBlocks: Boolean = true
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState = combine(
        settingsManager.aiEnabled,
        settingsManager.aiProvider,
        settingsManager.aiModel,
        settingsManager.aiApiKey,
        settingsManager.aiBypassBlocks
    ) { enabled, providerId, model, apiKey, bypass ->
        val provider = AiProvider.fromId(providerId)
        AiSettingsUiState(
            isAiEnabled = enabled,
            selectedProvider = provider,
            selectedModel = model.ifBlank { provider.getDefaultModel() },
            apiKey = apiKey,
            aiBypassBlocks = bypass
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettingsUiState())

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAiEnabled(enabled)
        }
    }

    fun setProvider(provider: AiProvider) {
        viewModelScope.launch {
            settingsManager.setAiProvider(provider.id)
            settingsManager.setAiModel(provider.getDefaultModel())
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch {
            settingsManager.setAiModel(model)
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.setAiApiKey(key)
        }
    }

    fun setAiBypassBlocks(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAiBypassBlocks(enabled)
        }
    }
}
