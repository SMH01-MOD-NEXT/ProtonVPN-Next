/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.probe.ServerAvailability
import ru.protonmod.next.data.network.probe.ServerProbeResult
import ru.protonmod.next.data.network.probe.ServerScanEngine
import ru.protonmod.next.data.network.probe.ServerScanManager
import ru.protonmod.next.data.network.probe.ServerScanObfuscationMode
import ru.protonmod.next.data.network.probe.ServerScanPortMode
import ru.protonmod.next.data.network.probe.ServerScanPreferences
import ru.protonmod.next.data.network.probe.ServerScanScope
import ru.protonmod.next.data.network.probe.ServerScanState
import ru.protonmod.next.data.repository.ServerProbeRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.utils.coroutines.DispatcherProvider

enum class ServerScanTarget { ALL, COUNTRY, SERVER }

/** Saved verdicts of every checked server address. */
data class ServerScanSummary(
    val available: Int = 0,
    val blocked: Int = 0,
    val unknown: Int = 0,
    val lastCheckedAt: Long? = null,
) {
    val total: Int
        get() = available + blocked + unknown

    companion object {
        fun of(results: Collection<ServerProbeResult>): ServerScanSummary {
            var available = 0
            var blocked = 0
            var unknown = 0
            var last = 0L
            for (result in results) {
                when (result.availability) {
                    ServerAvailability.AVAILABLE -> available++
                    ServerAvailability.BLOCKED -> blocked++
                    ServerAvailability.UNKNOWN -> unknown++
                }
                if (result.checkedAt > last) last = result.checkedAt
            }
            return ServerScanSummary(available, blocked, unknown, last.takeIf { it > 0L })
        }
    }
}

data class ServerScanUiState(
    val preferences: ServerScanPreferences = ServerScanPreferences(),
    val scan: ServerScanState = ServerScanState(),
    val target: ServerScanTarget = ServerScanTarget.ALL,
    val countryCodes: List<String> = emptyList(),
    val selectedCountry: String? = null,
    val countryServers: List<LogicalServer> = emptyList(),
    val selectedServer: LogicalServer? = null,
    val selectedServerResults: List<ServerProbeResult> = emptyList(),
    val summary: ServerScanSummary = ServerScanSummary(),
) {
    val canStart: Boolean
        get() = !scan.isRunning && when (target) {
            ServerScanTarget.ALL -> true
            ServerScanTarget.COUNTRY -> selectedCountry != null
            ServerScanTarget.SERVER -> selectedServer != null
        }
}

private data class ScanSelection(
    val target: ServerScanTarget = ServerScanTarget.ALL,
    val country: String? = null,
    val serverId: String? = null,
)

private data class ScanCatalog(
    val countryCodes: List<String> = emptyList(),
    val selectedCountry: String? = null,
    val countryServers: List<LogicalServer> = emptyList(),
    val selectedServer: LogicalServer? = null,
)

private data class ScanResultsInfo(
    val summary: ServerScanSummary = ServerScanSummary(),
    val selectedServerResults: List<ServerProbeResult> = emptyList(),
)

@HiltViewModel
class ServerScanViewModel @Inject constructor(
    private val scanManager: ServerScanManager,
    private val settingsManager: SettingsManager,
    private val probeRepository: ServerProbeRepository,
    vpnRepository: VpnRepository,
    sessionDao: SessionDao,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val selection = MutableStateFlow(ScanSelection())

    // Only servers that a scan really checks, so the pickers never offer a server that would be skipped.
    private val scannableServers = combine(
        vpnRepository.getServersFlow(),
        sessionDao.getSessionFlow().map { it?.userTier ?: 0 }.distinctUntilChanged(),
    ) { servers, userTier ->
        servers.filter { ServerScanEngine.isScannable(it, userTier) }
    }

    private val catalog: StateFlow<ScanCatalog> = combine(scannableServers, selection) { servers, current ->
        val codes = servers.mapTo(HashSet()) { it.exitCountry }.sorted()
        val country = current.country?.takeIf { it in codes }
        val countryServers = if (country == null) {
            emptyList()
        } else {
            servers.filter { it.exitCountry == country }.sortedBy { it.name }
        }
        ScanCatalog(
            countryCodes = codes,
            selectedCountry = country,
            countryServers = countryServers,
            selectedServer = current.serverId?.let { id -> countryServers.firstOrNull { it.id == id } },
        )
    }
        .flowOn(dispatcherProvider.default())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanCatalog())

    // A running scan saves results twice per second; the summary is counted off the main thread.
    private val resultsInfo = combine(probeRepository.results, catalog) { results, current ->
        ScanResultsInfo(
            summary = ServerScanSummary.of(results.values),
            selectedServerResults = current.selectedServer?.servers?.mapNotNull { results[it.id] }.orEmpty(),
        )
    }
        .distinctUntilChanged()
        .flowOn(dispatcherProvider.default())

    val uiState: StateFlow<ServerScanUiState> = combine(
        settingsManager.serverScanPreferences,
        scanManager.state,
        selection,
        catalog,
        resultsInfo,
    ) { preferences, scan, current, servers, results ->
        ServerScanUiState(
            preferences = preferences,
            scan = scan,
            target = current.target,
            countryCodes = servers.countryCodes,
            selectedCountry = servers.selectedCountry,
            countryServers = servers.countryServers,
            selectedServer = servers.selectedServer,
            selectedServerResults = results.selectedServerResults,
            summary = results.summary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerScanUiState())

    fun setTarget(target: ServerScanTarget) {
        selection.update { it.copy(target = target) }
    }

    fun selectCountry(code: String) {
        selection.update { if (it.country == code) it else it.copy(country = code, serverId = null) }
    }

    fun selectServer(id: String) {
        selection.update { it.copy(serverId = id) }
    }

    /** Starts a scan of the current selection. It keeps running when the screen is closed. */
    fun start() {
        val state = uiState.value
        val scope = when (state.target) {
            ServerScanTarget.ALL -> ServerScanScope.All
            ServerScanTarget.COUNTRY -> ServerScanScope.Country(state.selectedCountry ?: return)
            ServerScanTarget.SERVER -> ServerScanScope.Server(state.selectedServer?.id ?: return)
        }
        scanManager.start(scope)
    }

    fun stop() = scanManager.stop()

    fun clearResults() {
        if (scanManager.state.value.isRunning) return
        viewModelScope.launch { probeRepository.clear() }
    }

    fun setThreads(threads: Int) = updatePreferences { it.copy(threads = threads) }

    fun setTimeout(timeoutMs: Int) = updatePreferences { it.copy(timeoutMs = timeoutMs) }

    fun setAttempts(attempts: Int) = updatePreferences { it.copy(attempts = attempts) }

    fun setPortMode(mode: ServerScanPortMode) = updatePreferences { it.copy(portMode = mode) }

    fun setCustomPort(port: Int) = updatePreferences { it.copy(customPort = port) }

    fun setObfuscationMode(mode: ServerScanObfuscationMode) = updatePreferences { it.copy(obfuscationMode = mode) }

    fun setTcpCheck(enabled: Boolean) = updatePreferences { it.copy(tcpCheck = enabled) }

    fun setDataCheck(enabled: Boolean) = updatePreferences { it.copy(dataCheck = enabled) }

    private fun updatePreferences(transform: (ServerScanPreferences) -> ServerScanPreferences) {
        viewModelScope.launch { settingsManager.updateServerScanPreferences(transform) }
    }
}
