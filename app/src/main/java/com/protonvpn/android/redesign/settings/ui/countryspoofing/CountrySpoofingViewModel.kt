/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.settings.ui.countryspoofing

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.ViewModel
// Remove viewModelScope usage for the update action
import com.protonvpn.android.R
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.utils.Storage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CountrySpoofingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateServerListFromApi: UpdateServerListFromApi
) : ViewModel() {

    data class CountrySpoofingUiState(
        val isEnabled: Boolean = false,
        val isNullSpoof: Boolean = false,
        val countryCode: String = ""
    )

    private val _viewState = MutableStateFlow(CountrySpoofingUiState())
    val viewState = _viewState.asStateFlow()

    // Store the initial state to compare against on exit
    private var initialState: CountrySpoofingUiState

    private val sharedPreferences = context.getSharedPreferences("Storage", Context.MODE_PRIVATE)
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "spoof_country_enabled" || key == "spoof_country_null" || key == "spoof_country_code") {
            refreshState()
        }
    }

    init {
        // Capture initial state immediately
        initialState = getCurrentStateFromStorage()
        _viewState.value = initialState

        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun getCurrentStateFromStorage(): CountrySpoofingUiState {
        return CountrySpoofingUiState(
            isEnabled = Storage.getBoolean("spoof_country_enabled", false),
            isNullSpoof = Storage.getBoolean("spoof_country_null", false),
            countryCode = Storage.getString("spoof_country_code", "") ?: ""
        )
    }

    private fun refreshState() {
        _viewState.update { getCurrentStateFromStorage() }
    }

    fun toggleEnabled(enabled: Boolean) {
        Storage.saveBoolean("spoof_country_enabled", enabled)
    }

    fun toggleNullSpoof(enabled: Boolean) {
        Storage.saveBoolean("spoof_country_null", enabled)
    }

    fun setCountryCode(code: String) {
        Storage.saveString("spoof_country_code", code.uppercase())
    }

    /**
     * Called when the screen is closing.
     * Checks if the current state differs from the initial state.
     * If changed, triggers the API update.
     */
    fun onScreenExit() {
        val currentState = _viewState.value
        if (currentState != initialState) {
            triggerServerUpdate()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun triggerServerUpdate() {
        GlobalScope.launch(Dispatchers.IO) {
            // Force server list update to apply new headers
            updateServerListFromApi(
                netzone = null,
                freeOnlyNeeded = false,
                serverListLastModified = 0
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.settings_country_spoofing_update_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }
}