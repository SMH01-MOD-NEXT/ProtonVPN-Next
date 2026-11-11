/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.WarpPreferences
import ru.protonmod.next.data.repository.*
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager

data class WarpSettingsState(val preferences: WarpPreferences = WarpPreferences(), val config: CustomConfig? = null,
    val busy: Boolean = false, val message: Int? = null)

@HiltViewModel
class WarpSettingsViewModel @Inject constructor(
    private val settings: SettingsManager,
    private val configs: CustomConfigRepository,
    private val warp: WarpRepository,
    private val vpn: AmneziaVpnManager,
    private val connected: ConnectedServerState,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Int?>(null)
    val state = combine(settings.warpPreferences, configs.configs, busy, message) { p, c, b, m ->
        WarpSettingsState(p, c.firstOrNull { it.isWarp }, b, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WarpSettingsState())
    init { action { configs.load() } }
    fun clearMessage() { message.value = null }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message.value = R.string.custom_error }
        finally { busy.value = false }
    }
    fun save(preferences: WarpPreferences) = action {
        settings.setWarpPreferences(preferences)
        if (!preferences.enabled) stopWarpIfInUse()
        message.value = R.string.warp_saved
    }
    fun generate(preferences: WarpPreferences) = action {
        warp.generateAndSave(preferences)
        message.value = R.string.warp_generated
    }
    private suspend fun stopWarpIfInUse() {
        val current = connected.connectedServer.value
        val warpConfig = configs.configs.value.firstOrNull { it.isWarp } ?: return
        val direct = current?.id?.removePrefix("custom:")?.let { CustomConfigRules.find(listOf(warpConfig), it) } != null
        val chain = current?.id?.startsWith("custom:") != true && settings.proxyChainEnabled.first() && settings.proxyChainConfig.first().lineSequence().any {
            it.trim().startsWith("custom://") && CustomConfigRules.find(listOf(warpConfig), it.trim().removePrefix("custom://")) != null
        }
        if (direct || chain) vpn.disconnect() // Never silently drop a disabled hop and connect directly.
    }
    fun delete() = action {
        stopWarpIfInUse()
        configs.delete(CustomConfigRules.WARP_ID)
        settings.setWarpPreferences(settings.warpPreferences.first().copy(enabled = false))
    }
    fun addHop() = action {
        val config = requireNotNull(configs.configs.value.firstOrNull { it.isWarp })
        require(settings.warpPreferences.first().enabled)
        settings.setProxyChainConfig(CustomConfigRules.appendHop(settings.proxyChainConfig.first(), config))
        settings.setProxyChainEnabled(true)
        message.value = R.string.custom_hop_added
    }
}
