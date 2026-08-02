/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.protonmod.next.data.ai.AiManager
import ru.protonmod.next.data.ai.AiProposal
import ru.protonmod.next.data.ai.AiResult
import ru.protonmod.next.data.local.SettingsManager

@HiltViewModel
class AiOverlayViewModel @Inject constructor(
    private val aiManager: AiManager,
    settingsManager: SettingsManager,
) : ViewModel() {
    private val _isVisible = MutableStateFlow(false)
    val isVisible = _isVisible.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _proposal = MutableStateFlow<AiProposal?>(null)
    val proposal = _proposal.asStateFlow()

    val aiEnabled = settingsManager.aiEnabled

    fun show() {
        _isVisible.value = true
        _statusMessage.value = null
    }

    fun hide() {
        _isVisible.value = false
        _statusMessage.value = null
        _proposal.value = null
    }

    fun submitQuery(query: String) = requestProposal(query, null)

    fun refineProposal(query: String) {
        val current = _proposal.value ?: return
        requestProposal(query, current)
    }

    fun dismissProposal() {
        _proposal.value = null
        _statusMessage.value = null
    }

    fun applyProposal() {
        val current = _proposal.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null
            when (val result = aiManager.applyProposal(current)) {
                AiResult.Success -> {
                    _statusMessage.value = "ai_success"
                    _proposal.value = null
                    delay(1_200)
                    hide()
                }
                AiResult.NoApiKey -> _statusMessage.value = "ai_error_no_key"
                is AiResult.Error -> _statusMessage.value = result.message
                is AiResult.ProposalReady -> Unit
            }
            _isProcessing.value = false
        }
    }

    private fun requestProposal(query: String, current: AiProposal?) {
        if (query.isBlank() || _isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = null
            when (val result = aiManager.processQuery(query, current)) {
                is AiResult.ProposalReady -> _proposal.value = result.proposal
                AiResult.NoApiKey -> _statusMessage.value = "ai_error_no_key"
                is AiResult.Error -> _statusMessage.value = result.message
                AiResult.Success -> Unit
            }
            _isProcessing.value = false
        }
    }
}
