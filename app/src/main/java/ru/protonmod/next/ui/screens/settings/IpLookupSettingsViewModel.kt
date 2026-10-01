/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    fun setEnabled(ids: Set<String>, enabled: Boolean) = viewModelScope.launch {
        settings.setIpLookupService(ids, enabled)
    }
    fun disableAll() = viewModelScope.launch { settings.disableIpLookup() }
}
