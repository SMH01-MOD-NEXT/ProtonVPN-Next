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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.protonmod.next.data.ai.AiManager
import ru.protonmod.next.data.ai.AiResult
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

@HiltViewModel
class AiOverlayViewModel @Inject constructor(
    private val aiManager: AiManager,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val _isVisible = MutableStateFlow(false)
    val isVisible = _isVisible.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val aiEnabled = settingsManager.aiEnabled

    fun show() {
        _isVisible.value = true
        _statusMessage.value = null
    }

    fun hide() {
        _isVisible.value = false
    }

    fun submitQuery(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null
            
            val result = aiManager.processQuery(query)
            
            when (result) {
                is AiResult.Success -> {
                    _statusMessage.value = "ai_success"
                }
                is AiResult.NoApiKey -> {
                    _statusMessage.value = "ai_error_no_key"
                }
                is AiResult.Error -> {
                    _statusMessage.value = result.message
                }
            }
            
            _isProcessing.value = false
            if (result is AiResult.Success) {
                delay(2000)
                hide()
            }
        }
    }
}
