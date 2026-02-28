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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.AbstractBackend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.state.ConnectedServerState
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.inject.Inject

/**
 * Intermediate base class to help Hilt/KSP resolve the Service inheritance 
 * from the library's nested class.
 */
open class AmneziaVpnServiceBase : AbstractBackend.VpnService()

/**
 * Service implementation for AmneziaWG tunnel.
 */
@AndroidEntryPoint
class ProtonVpnService : AmneziaVpnServiceBase() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statsJob: Job? = null
    private var lastRx: Long = 0L
    private var lastTx: Long = 0L
    private var lastSpeedText: String? = null
    private var notificationsEnabled: Boolean = true
    private var killSwitchEnabled: Boolean = false
    private var isManualDisconnect: Boolean = false

    companion object {
        private const val TAG = "ProtonVpnService"
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        
        const val ACTION_UPDATE_SETTINGS = "ru.protonmod.next.vpn.UPDATE_SETTINGS"
        const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val EXTRA_KILL_SWITCH_ENABLED = "kill_switch_enabled"

        const val TUNNEL_NAME = "proton_awg"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val CHANNEL_SILENT_ID = "vpn_status_channel_silent"
        
        const val STATE_CONNECTING = "CONNECTING"
    }

    private lateinit var backend: GoBackend
    private var currentTunnelState: Tunnel.State = Tunnel.State.DOWN
    private var isCurrentlyConnecting: Boolean = false

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        
        override fun onStateChange(newState: Tunnel.State) {
            if (currentTunnelState == newState) return
            currentTunnelState = newState
            isCurrentlyConnecting = false

            Log.d(TAG, "State changed to $newState")
            
            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, newState.name)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)

            if (newState == Tunnel.State.DOWN) {
                stopTrafficUpdates()
            }
            
            updateNotification(newState.name)
            
            if (newState == Tunnel.State.UP) {
                startTrafficUpdates()
            }
        }

        override fun isIpv4ResolutionPreferred(): Boolean = true

        override fun isMetered(): Boolean = false
    }

    override fun onCreate() {
        Log.d(TAG, "VPN Service creating in isolated :vpn process")

        try {
            Os.setenv("TMPDIR", cacheDir.absolutePath, true)
            Os.setenv("WG_TUN_DIR", cacheDir.absolutePath, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables", e)
        }

        super.onCreate()
        createNotificationChannels()

        backend = GoBackend(this, object : TunnelActionHandler {
            override fun runPreUp(scripts: Collection<String>) {}
            override fun runPostUp(scripts: Collection<String>) {}
            override fun runPreDown(scripts: Collection<String>) {}
            override fun runPostDown(scripts: Collection<String>) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service start command received: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                isManualDisconnect = false
                val configStr = intent.getStringExtra(EXTRA_CONFIG)
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, false)

                if (configStr != null) {
                    serviceScope.launch {
                        try {
                            val configStream = ByteArrayInputStream(configStr.toByteArray())
                            val config = Config.parse(configStream)

                            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                                putExtra(EXTRA_STATE, STATE_CONNECTING)
                                setPackage(packageName)
                            }
                            sendBroadcast(broadcast)
                            
                            isCurrentlyConnecting = true
                            updateNotification(STATE_CONNECTING)

                            backend.setState(tunnel, Tunnel.State.UP, config)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start tunnel", e)
                            tunnel.onStateChange(Tunnel.State.DOWN)
                        }
                    }
                }
            }
            ACTION_DISCONNECT -> {
                isManualDisconnect = true
                isCurrentlyConnecting = false
                try {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop tunnel", e)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_UPDATE_SETTINGS -> {
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
                Log.d(TAG, "Settings updated: notifications=$notificationsEnabled, killSwitch=$killSwitchEnabled")
                
                val label = when {
                    isCurrentlyConnecting -> STATE_CONNECTING
                    else -> currentTunnelState.name
                }
                
                updateNotification(label)
            }
            else -> {
                return super.onStartCommand(intent, flags, startId)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val name = getString(R.string.notification_channel_name)
            
            // Standard channel
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // Silent channel
            val silentChannel = NotificationChannel(CHANNEL_SILENT_ID, "$name (Silent)", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    private fun startTrafficUpdates() {
        stopTrafficUpdates()
        lastRx = 0L
        lastTx = 0L
        statsJob = serviceScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    try {
                        val stats = backend.getStatistics(tunnel)
                        val totalRx = stats.totalRx()
                        val totalTx = stats.totalTx()

                        val deltaRx = if (lastRx == 0L) 0L else (totalRx - lastRx)
                        val deltaTx = if (lastTx == 0L) 0L else (totalTx - lastTx)

                        lastRx = totalRx
                        lastTx = totalTx

                        val upStr = formatSpeed(deltaTx)
                        val downStr = formatSpeed(deltaRx)
                        lastSpeedText = "↑ $upStr ↓ $downStr"

                        if (notificationsEnabled) {
                            serviceScope.launch {
                                updateNotification(Tunnel.State.UP.name)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while fetching stats", e)
                    }
                    delay(1000)
                }
            } finally {
                Log.d(TAG, "Traffic updates coroutine finished")
            }
        }
    }

    private fun stopTrafficUpdates() {
        statsJob?.cancel()
        statsJob = null
        lastSpeedText = null
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val b = bytesPerSec.toDouble()
        if (b <= 0.0) return "0 B/s"
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return when {
            b >= gib -> String.format(Locale.US, "%.2f GiB/s", b / gib)
            b >= mib -> String.format(Locale.US, "%.2f MiB/s", b / mib)
            b >= kib -> String.format(Locale.US, "%.1f KiB/s", b / kib)
            else -> String.format(Locale.US, "%.0f B/s", b)
        }
    }

    private fun createNotification(stateName: String, speedText: String? = null): android.app.Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: "Proton VPN"

        val title = when (stateName) {
            Tunnel.State.UP.name -> getString(R.string.notification_title_connected, serverName)
            STATE_CONNECTING -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }

        val disconnectIntent = Intent(this, ProtonVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val activeChannelId = if (notificationsEnabled) CHANNEL_ID else CHANNEL_SILENT_ID

        val builder = NotificationCompat.Builder(this, activeChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(if (notificationsEnabled) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setOngoing(stateName != Tunnel.State.DOWN.name)
            .setContentIntent(contentPendingIntent)
            .setShowWhen(false)

        if (stateName == Tunnel.State.UP.name) {
            builder.addAction(
                0,
                getString(R.string.notification_action_disconnect),
                disconnectPendingIntent
            )
            if (!speedText.isNullOrEmpty() && notificationsEnabled) {
                builder.setContentText(speedText)
            }
        }

        return builder.build()
    }

    private fun updateNotification(stateName: String) {
        val isDown = stateName == Tunnel.State.DOWN.name
        val isConnecting = isCurrentlyConnecting || stateName == STATE_CONNECTING
        
        // Decide if we should show a notification.
        val shouldShow = when {
            isConnecting -> true
            isDown -> killSwitchEnabled && !isManualDisconnect
            else -> notificationsEnabled
        }

        if (shouldShow) {
            val notification = createNotification(stateName, lastSpeedText)
            startForeground(NOTIFICATION_ID, notification)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            if (isDown) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroyed")
        serviceScope.cancel()
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN on destroy", e)
        }
        super.onDestroy()
    }
}
