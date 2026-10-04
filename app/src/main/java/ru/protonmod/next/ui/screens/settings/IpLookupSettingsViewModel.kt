/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.ip.IpEchoSources

@HiltViewModel
class IpLookupSettingsViewModel @Inject constructor(private val settings: SettingsManager) : ViewModel() {
    val enabledIds = settings.ipLookupServices.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), IpEchoSources.DEFAULT_ENABLED_IDS,
    )
    val catalogue = settings.ipLookupCatalogue.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), IpEchoSources.catalogue(),
    )
    private val _updateFailed = MutableStateFlow(false)
    val updateFailed = _updateFailed.asStateFlow()

    private fun update(block: suspend () -> Unit) = viewModelScope.launch {
        _updateFailed.value = false
        try {
            block()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _updateFailed.value = true
            ru.protonmod.next.utils.ProtonLogger.w("IpLookupSettings", "Preference update failed: ${error.message}")
        }
    }
    fun setEnabled(ids: Set<String>, enabled: Boolean) = update { settings.setIpLookupService(ids, enabled) }
}
