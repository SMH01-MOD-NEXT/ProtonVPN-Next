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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.state.ConnectedServerState
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.inject.Inject

/**
 * Service implementation for AmneziaWG tunnel.
 *
 * This VPN Service is declared with foregroundServiceType="specialUse" to comply with Android 14+ requirements.
 * According to Android VPN framework documentation, VpnService class is specifically designed for apps
 * that provide core VPN functionality and fall under VPN use case exceptions.
 *
 * The service runs in an isolated :vpn process to prevent Go runtime conflicts with SRP authentication.
 *
 * For Android 14+, the PROPERTY_SPECIAL_USE_FGS_SUBTYPE is set to "VPN" to declare the special use case.
 */
@AndroidEntryPoint
class ProtonVpnService : GoBackend.VpnService() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statsJob: Job? = null
    private var lastRx: Long = 0L
    private var lastTx: Long = 0L

    companion object {
        private const val TAG = "ProtonVpnService"
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val TUNNEL_NAME = "proton_awg"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
    }

    private lateinit var backend: GoBackend

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "State changed to $newState")
            updateNotification(newState)
            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, newState.name)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)

            if (newState == Tunnel.State.DOWN) {
                stopTrafficUpdates()
                stopForeground(true)
            } else if (newState == Tunnel.State.UP) {
                startTrafficUpdates()
            }
        }
    }

    override fun onCreate() {
        Log.d(TAG, "VPN Service creating in isolated :vpn process")

        Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        Os.setenv("WG_TUN_DIR", cacheDir.absolutePath, true)

        super.onCreate()
        createNotificationChannel()
        backend = GoBackend(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service start command received: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                val configStr = intent.getStringExtra(EXTRA_CONFIG)
                if (configStr != null) {
                    serviceScope.launch {
                        try {
                            val killSwitch = settingsManager.killSwitchEnabled.first()

                            val configStream = ByteArrayInputStream(configStr.toByteArray())
                            val config = Config.parse(configStream)

                            // Get split tunneling excluded apps
                            val excludedApps = intent.getStringArrayListExtra(EXTRA_EXCLUDED_APPS)?.toSet() ?: emptySet()
                            val excludedIps = intent.getStringArrayListExtra(EXTRA_EXCLUDED_IPS)?.toSet() ?: emptySet()

                            Log.d(TAG, "Split Tunneling - Excluded Apps: ${excludedApps.size}, Excluded IPs: ${excludedIps.size}")

                            // Apply Kill Switch if enabled
                            if (killSwitch) {
                                Log.d(TAG, "Kill Switch is enabled")
                            }

                            // GoBackend will apply excluded/included applications from the Interface inside Config
                            backend.setState(tunnel, Tunnel.State.UP, config)

                            startForeground(NOTIFICATION_ID, createNotification(Tunnel.State.TOGGLE))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start tunnel", e)
                            tunnel.onStateChange(Tunnel.State.DOWN)
                        }
                    }
                }
            }
            ACTION_DISCONNECT -> {
                try {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop tunnel", e)
                }
            }
            else -> {
                return super.onStartCommand(intent, flags, startId)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
                        val speedText = "↑ $upStr ↓ $downStr"

                        // Update notification with speeds
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val notification = createNotification(Tunnel.State.UP, speedText)
                        notificationManager.notify(NOTIFICATION_ID, notification)
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

    private fun createNotification(state: Tunnel.State, speedText: String? = null): android.app.Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: "Proton VPN"

        val title = when (state) {
            Tunnel.State.UP -> getString(R.string.notification_title_connected, serverName)
            Tunnel.State.TOGGLE -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }

        val disconnectIntent = Intent(this, ProtonVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open the app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this exists or use a default
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(state != Tunnel.State.DOWN)
            .setContentIntent(contentPendingIntent)

        if (state == Tunnel.State.UP) {
            builder.addAction(
                0,
                getString(R.string.notification_action_disconnect),
                disconnectPendingIntent
            )
            if (!speedText.isNullOrEmpty()) {
                builder.setContentText(speedText)
            }
        }

        return builder.build()
    }

    private fun updateNotification(state: Tunnel.State) {
        serviceScope.launch {
            val notificationsEnabled = settingsManager.notificationsEnabled.first()
            if (notificationsEnabled) {
                val notification = createNotification(state)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
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
