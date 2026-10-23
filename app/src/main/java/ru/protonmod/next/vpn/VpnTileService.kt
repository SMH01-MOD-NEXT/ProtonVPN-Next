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

package ru.protonmod.next.vpn

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.protonmod.next.MainActivity
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import javax.inject.Inject

@AndroidEntryPoint
class VpnTileService : TileService() {

    @Inject
    lateinit var amneziaVpnManager: AmneziaVpnManager

    @Inject
    lateinit var vpnRepository: VpnRepository

    @Inject
    lateinit var quickConnect: VpnQuickConnect

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var profileDao: ProfileDao

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /** Cancel tile observations when this service is destroyed. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var observationJob: Job? = null

    // Keep every binding alive until destruction, including bindings created on rebind.
    private val tileBinders = mutableListOf<ReleasableTileServiceBinder>()

    override fun onBind(intent: Intent): IBinder? {
        val delegate = super.onBind(intent) ?: return null
        return ReleasableTileServiceBinder(delegate).also { tileBinders += it }
    }

    override fun onStartListening() {
        super.onStartListening()
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            combine(
                amneziaVpnManager.tunnelState,
                settingsManager.quickConnectStrategy,
                settingsManager.quickConnectTargetId,
                connectedServerState.connectedServer
            ) { state, strategy, targetId, connectedServer ->
                UpdateParams(state, strategy, targetId, connectedServer)
            }.collect { params ->
                updateTile(params.state, params.strategy, params.targetId, params.connectedServer)
            }
        }
    }

    private data class UpdateParams(
        val state: VpnTunnelState,
        val strategy: String,
        val targetId: String?,
        val connectedServer: LogicalServer?
    )

    override fun onStopListening() {
        super.onStopListening()
        observationJob?.cancel()
        observationJob = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        observationJob = null
        try {
            super.onDestroy()
        } finally {
            // SystemUI/native Binder may keep the exported binder after Service destruction.
            // Only the detached wrapper may survive, not TileService's inner stub and service.
            tileBinders.forEach { it.release() }
            tileBinders.clear()
        }
    }

    override fun onClick() {
        super.onClick()
        val currentState = amneziaVpnManager.tunnelState.value
        
        if (currentState == VpnTunnelState.UP) {
            amneziaVpnManager.disconnect()
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val wrapper = PendingIntentActivityWrapper(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
                TileServiceCompat.startActivityAndCollapse(this, wrapper)
            } else {
                // Launch on applicationScope: the VPN connection must proceed
                // even if this TileService instance is destroyed mid-connect.
                val connector = quickConnect
                applicationScope.launch {
                    connector.connect()
                }
            }
        }
    }

    private suspend fun updateTile(
        state: VpnTunnelState,
        strategy: String,
        targetId: String?,
        connectedServer: LogicalServer?
    ) {
        val tile = qsTile ?: return
        
        tile.state = when (state) {
            VpnTunnelState.UP -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        val subtitle = if (state == VpnTunnelState.UP && connectedServer != null) {
            getString(R.string.tile_subtitle_connected, connectedServer.name)
        } else {
            when (strategy) {
                "fastest" -> getString(R.string.tile_subtitle_fastest)
                "recent" -> getString(R.string.tile_subtitle_last)
                "server" -> {
                    val serverName = vpnRepository.getCachedServers().find { it.id == targetId }?.name ?: "..."
                    getString(R.string.tile_subtitle_specific, serverName)
                }
                "profile" -> {
                    val profileName = profileDao.getAllProfilesFlow().first().find { it.id == targetId }?.name ?: "..."
                    getString(R.string.tile_subtitle_specific, profileName)
                }
                else -> getString(R.string.tile_subtitle_fastest)
            }
        }

        tile.subtitle = subtitle

        tile.updateTile()
    }
}
