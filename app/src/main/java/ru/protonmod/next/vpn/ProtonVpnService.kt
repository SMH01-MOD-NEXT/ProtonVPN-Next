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

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.system.Os
import io.sentry.SentryLevel
import ru.protonmod.next.utils.ProtonLogger
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
 * Service implementation for AmneziaWG tunnel used in Proton VPN-Next.
 * Manages the VPN lifecycle, foreground notifications, and network traffic statistics.
 */
@AndroidEntryPoint
class ProtonVpnService : AmneziaVpnServiceBase() {

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    // SupervisorJob ensures that if one child coroutine fails, it doesn't crash the whole scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statsJob: Job? = null
    private var logcatJob: Job? = null
    private var lastRx: Long = 0L
    private var lastTx: Long = 0L
    private var lastSpeedText: String? = null

    private var notificationsEnabled: Boolean = true
    private var killSwitchEnabled: Boolean = false
    private var isManualDisconnect: Boolean = false
    private var isVerified: Boolean = false
    private var lastLogicalServerId: String? = null

    // Cached PendingIntent objects to reduce IPC calls to system service
    // These are reused across notification updates to avoid DeadSystemException
    // when the system PendingIntent service becomes temporarily unavailable
    private var cachedDisconnectPendingIntent: PendingIntent? = null
    private var cachedContentPendingIntent: PendingIntent? = null

    companion object {
        private const val TAG = "ProtonVpnService"

        // Intent Actions
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val ACTION_UPDATE_SETTINGS = "ru.protonmod.next.vpn.UPDATE_SETTINGS"
        const val ACTION_STATS_UPDATED = "ru.protonmod.next.vpn.STATS_UPDATED"
        const val ACTION_SET_VERIFIED = "ru.protonmod.next.vpn.SET_VERIFIED"
        const val ACTION_QUERY_STATE = "ru.protonmod.next.vpn.QUERY_STATE"

        // Intent Extras
        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val EXTRA_STATE = "state"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TRAFFIC_RX = "traffic_rx"
        const val EXTRA_TRAFFIC_TX = "traffic_tx"
        const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val EXTRA_KILL_SWITCH_ENABLED = "kill_switch_enabled"
        const val EXTRA_NON_FATAL_ENABLED = "non_fatal_enabled"
        const val EXTRA_ANALYTICS_ENABLED = "analytics_enabled"
        const val EXTRA_LOGICAL_SERVER_ID = "logical_server_id"

        const val TUNNEL_NAME = "proton_awg"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val CHANNEL_SILENT_ID = "vpn_status_channel_silent"

        const val STATE_CONNECTING = "CONNECTING"
    }

    private lateinit var backend: GoBackend
    private var currentTunnelState: Tunnel.State = Tunnel.State.DOWN
    private var isCurrentlyConnecting: Boolean = false
    private var isForegroundServiceStarted: Boolean = false

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Tunnel callback interface to monitor VPN states and preferences.
     */
    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            val wasConnecting = isCurrentlyConnecting
            if (currentTunnelState == newState && !wasConnecting) return
            
            currentTunnelState = newState
            isCurrentlyConnecting = false

            ProtonLogger.d(TAG, "VPN State changed to $newState (wasConnecting=$wasConnecting)")
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN State Changed: $newState", SentryLevel.INFO, "vpn.state")

            // Broadcast the new state to the rest of the application
            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, newState.name)
                putExtra(EXTRA_LOGICAL_SERVER_ID, lastLogicalServerId)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)

            // Handle traffic updates based on the current state
            if (newState == Tunnel.State.DOWN) {
                stopTrafficUpdates()
                stopLogcatCollection()
            }

            updateNotification(newState.name)

            if (newState == Tunnel.State.UP) {
                startTrafficUpdates()
                // Log collection is already started in ACTION_CONNECT,
                // but we ensure it's active here just in case of unexpected state transitions.
                startLogcatCollection()
            }
        }

        override fun isIpv4ResolutionPreferred(): Boolean = true

        override fun isMetered(): Boolean = false
    }

    /**
     * BroadcastReceiver to dynamically update settings (like notifications/kill switch)
     * without restarting the VPN service.
     */
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_SETTINGS -> {
                    notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                    killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)

                    if (intent.hasExtra(EXTRA_NON_FATAL_ENABLED)) {
                        val nonFatal = intent.getBooleanExtra(EXTRA_NON_FATAL_ENABLED, true)
                        ProtonLogger.isNonFatalEnabled = nonFatal
                    }

                    if (intent.hasExtra(EXTRA_ANALYTICS_ENABLED)) {
                        val analytics = intent.getBooleanExtra(EXTRA_ANALYTICS_ENABLED, true)
                        ProtonLogger.isAnalyticsEnabled = analytics
                    }

                    ProtonLogger.d(TAG, "Settings updated via broadcast: notifications=$notificationsEnabled, killSwitch=$killSwitchEnabled, nonFatal=${ProtonLogger.isNonFatalEnabled}, analytics=${ProtonLogger.isAnalyticsEnabled}")

                    val label = when {
                        isCurrentlyConnecting -> STATE_CONNECTING
                        else -> currentTunnelState.name
                    }

                    updateNotification(label)
                }
                ACTION_DISCONNECT -> {
                    // Handles disconnect requests sent as a broadcast (e.g. from a backgrounded app).
                    // This mirrors the ACTION_DISCONNECT branch in onStartCommand but is reachable
                    // without startService(), avoiding BackgroundServiceStartNotAllowedException.
                    ProtonLogger.i(TAG, "Action DISCONNECT via broadcast: Stopping tunnel gracefully")
                    isManualDisconnect = true
                    isCurrentlyConnecting = false
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            backend.setState(tunnel, Tunnel.State.DOWN, null)
                        } catch (e: Exception) {
                            ProtonLogger.e(TAG, "Failed to stop VPN tunnel cleanly via broadcast", e)
                            stopForegroundOrService()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        ProtonLogger.i(TAG, "VPN Service creating in isolated :vpn process (PID: ${android.os.Process.myPid()})")

        // Verify 64-bit runtime (failsafe for 32-bit device detection)
        if (System.getProperty("ro.product.cpu.abi")?.contains("armeabi") == true ||
            System.getProperty("ro.product.cpu.abi")?.contains("x86") == true &&
            System.getProperty("ro.product.cpu.abi")?.contains("x86_64") == false) {
            ProtonLogger.e(TAG, "FATAL: App requires 64-bit CPU (arm64-v8a or x86_64). This device is 32-bit and not supported.")
        }

        // Set environment variables required for the Go backend (WireGuard/AmneziaWG)
        try {
            Os.setenv("TMPDIR", cacheDir.absolutePath, true)
            Os.setenv("WG_TUN_DIR", cacheDir.absolutePath, true)
            ProtonLogger.d(TAG, "Backend environment variables initialized")
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Failed to set environment variables for the backend", e)
        }

        super.onCreate()
        createNotificationChannels()

        // Register the dynamic settings receiver — also handles ACTION_DISCONNECT broadcast
        // so the tunnel can be stopped from a backgrounded app without startService().
        val filter = IntentFilter(ACTION_UPDATE_SETTINGS).apply {
            addAction(ACTION_DISCONNECT)
        }
        ContextCompat.registerReceiver(this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Initialize the Go backend
        backend = GoBackend(this, object : TunnelActionHandler {
            override fun runPreUp(scripts: Collection<String>) {}
            override fun runPostUp(scripts: Collection<String>) {}
            override fun runPreDown(scripts: Collection<String>) {}
            override fun runPostDown(scripts: Collection<String>) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        ProtonLogger.i(TAG, "Service start command: $action (StartID: $startId, Flags: $flags)")

        when (action) {
            ACTION_CONNECT -> {
                isManualDisconnect = false
                isVerified = false
                val configStr = intent.getStringExtra(EXTRA_CONFIG)
                lastLogicalServerId = intent.getStringExtra(EXTRA_LOGICAL_SERVER_ID)
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, false)

                ProtonLogger.d(TAG, "Action CONNECT: notifications=$notificationsEnabled, killSwitch=$killSwitchEnabled")

                // Important: Show connecting notification immediately to satisfy
                // Android's Foreground Service requirements and prevent exceptions.
                isCurrentlyConnecting = true
                startLogcatCollection() // Start early to capture handshake and init logs
                updateNotification(STATE_CONNECTING)

                if (configStr != null) {
                    ProtonLogger.v(TAG, "Connection config received (Length: ${configStr.length})")
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val configStream = ByteArrayInputStream(configStr.toByteArray())
                            val config = Config.parse(configStream)

                            ProtonLogger.i(TAG, "Config parsed successfully. Bringing tunnel UP...")

                            // Broadcast connecting state to UI
                            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                                putExtra(EXTRA_STATE, STATE_CONNECTING)
                                putExtra(EXTRA_LOGICAL_SERVER_ID, lastLogicalServerId)
                                setPackage(packageName)
                            }
                            sendBroadcast(broadcast)

                            // Bring the tunnel up
                            backend.setState(tunnel, Tunnel.State.UP, config)
                        } catch (e: Exception) {
                            ProtonLogger.e(TAG, "Critical failure during tunnel startup", e)
                            tunnel.onStateChange(Tunnel.State.DOWN)
                        }
                    }
                } else {
                    ProtonLogger.e(TAG, "Action CONNECT received but config string is NULL")
                    stopForegroundOrService()
                }
            }
            ACTION_DISCONNECT -> {
                ProtonLogger.i(TAG, "Action DISCONNECT: Stopping tunnel gracefully")
                isManualDisconnect = true
                isCurrentlyConnecting = false
                try {
                    // Bring the tunnel down gracefully
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    ProtonLogger.e(TAG, "Failed to stop VPN tunnel cleanly", e)
                    stopForegroundOrService()
                }
            }
            ACTION_UPDATE_SETTINGS -> {
                // ... (unchanged)
            }
            ACTION_SET_VERIFIED -> {
                isVerified = true
                val label = if (currentTunnelState == Tunnel.State.UP) Tunnel.State.UP.name else STATE_CONNECTING
                updateNotification(label)
            }
            ACTION_QUERY_STATE -> {
                ProtonLogger.d(TAG, "Action QUERY_STATE: Broadcasting current status")
                val stateBroadcast = Intent(ACTION_STATE_CHANGED).apply {
                    val label = if (isCurrentlyConnecting) STATE_CONNECTING else currentTunnelState.name
                    putExtra(EXTRA_STATE, label)
                    putExtra(EXTRA_LOGICAL_SERVER_ID, lastLogicalServerId)
                    setPackage(packageName)
                }
                sendBroadcast(stateBroadcast)

                if (currentTunnelState == Tunnel.State.UP) {
                    val speedBroadcast = Intent(ACTION_STATS_UPDATED).apply {
                        putExtra(EXTRA_SPEED, lastSpeedText)
                        putExtra(EXTRA_LOGICAL_SERVER_ID, lastLogicalServerId)
                        // Note: Traffic Rx/Tx are usually only available in the statsJob, 
                        // but if it's already running, it will send the next update soon.
                        setPackage(packageName)
                    }
                    sendBroadcast(speedBroadcast)
                }
            }
            else -> {
                return super.onStartCommand(intent, flags, startId)
            }
        }
        return START_STICKY
    }

    /**
     * Creates notification channels.
     */
    private fun createNotificationChannels() {
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val name = getString(R.string.notification_channel_name)

        // Standard channel for visible VPN status
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        // Silent channel for background operation without disturbing the user
        val silentChannel = NotificationChannel(CHANNEL_SILENT_ID, "$name (Silent)", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(silentChannel)
    }

    /**
     * Periodically queries the backend for network statistics and updates the notification.
     */
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
                        lastSpeedText = getString(R.string.vpn_speed_format, upStr, downStr)

                        val totalRxStr = formatBytes(totalRx, false)
                        val totalTxStr = formatBytes(totalTx, false)

                        // Broadcast speed updates to UI components
                        val speedBroadcast = Intent(ACTION_STATS_UPDATED).apply {
                            putExtra(EXTRA_SPEED, lastSpeedText)
                            putExtra(EXTRA_TRAFFIC_RX, totalRxStr)
                            putExtra(EXTRA_TRAFFIC_TX, totalTxStr)
                            putExtra(EXTRA_LOGICAL_SERVER_ID, lastLogicalServerId)
                            setPackage(packageName)
                        }
                        sendBroadcast(speedBroadcast)

                        if (notificationsEnabled && currentTunnelState == Tunnel.State.UP) {
                            // Update notification directly on the IO thread to avoid blocking the main thread.
                            // notificationManager.notify() is a Binder IPC call that can block for several
                            // seconds on slower devices; running it on IO prevents ANR on the main thread.
                            updateNotification(Tunnel.State.UP.name, isSpeedUpdateOnly = true)
                        }
                    } catch (e: Exception) {
                        ProtonLogger.e(TAG, "Error while fetching traffic statistics", e)
                    }
                    delay(1000) // Update frequency
                }
            } finally {
                ProtonLogger.d(TAG, "Traffic updates coroutine finished")
            }
        }
    }

    private fun stopTrafficUpdates() {
        statsJob?.cancel()
        statsJob = null

        // Clear cached PendingIntent objects to allow fresh creation on next connection
        cachedDisconnectPendingIntent = null
        cachedContentPendingIntent = null

        // Log final session stats
        val totalRx = lastRx
        val totalTx = lastTx
        ProtonLogger.i(TAG, "VPN Session ended. Final stats: RX=${formatSpeed(totalRx)}, TX=${formatSpeed(totalTx)}")
        ProtonLogger.addSentryBreadcrumb(TAG, "VPN Session Ended: RX=$totalRx, TX=$totalTx", SentryLevel.INFO, "vpn.stats")

        lastSpeedText = null
    }

    /**
     * Starts background collection of tunnel-specific logs from Logcat
     * and explicitly forwards critical AmneziaWG logs to Sentry as Breadcrumbs.
     *
     * To prevent CPU saturation (which can cause background ANRs on the main thread):
     * - Repetitive log lines are deduplicated within a rolling time window.
     * - Unimportant logs are filtered out using isImportantAwgLog().
     * - A small coroutine yield is inserted between each line so the IO thread
     * is not monopolised, allowing other work to be scheduled.
     * - The expensive Sentry Logs API (addSentryLog) is intentionally NOT called
     * here; breadcrumbs alone are sufficient for tunnel diagnostics.
     */
    @SuppressLint("LogTagMismatch")
    private fun startLogcatCollection() {
        if (logcatJob?.isActive == true) {
            ProtonLogger.v(TAG, "Logcat collection already running, skipping restart.")
            return
        }
        logcatJob?.cancel()
        logcatJob = serviceScope.launch(Dispatchers.IO) {
            ProtonLogger.d(TAG, "Starting Logcat collection for 'Tun/proton_awg'")
            val process = try {
                // BUGFIX: Use :D (Debug) instead of :V (Verbose) to eliminate empty log spam.
                val command = arrayOf(
                    "logcat",
                    "-v", "tag",
                    "-T", "1",
                    "--pid=${android.os.Process.myPid()}",
                    "Tun/proton_awg:D",
                    "tun/proton_awg:D",
                    "*:S"
                )
                Runtime.getRuntime().exec(command)
            } catch (e: Exception) {
                ProtonLogger.e(TAG, "Failed to start Logcat process", e)
                return@launch
            }

            // Deduplication: track last seen message and when it was last forwarded.
            // High-frequency identical messages (e.g. repeated handshake/keepalive lines
            // during a degraded tunnel) are suppressed to avoid flooding Sentry and
            // saturating DefaultDispatcher-worker threads.
            var lastLine = ""
            var lastLineEmittedAt = 0L
            val deduplicationWindowMs = 5_000L // suppress exact duplicates within 5 s

            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!isActive) return@useLines

                        // logcat with '-v tag' outputs format: "D/Tun/proton_awg: actual message"
                        // We find the colon and extract only the message part.
                        val msgSeparatorIndex = line.indexOf(": ")
                        if (msgSeparatorIndex == -1) return@forEach

                        val cleanLine = line.substring(msgSeparatorIndex + 2).trim()

                        // Drop completely empty logs or logs that are not considered important
                        // to reduce spam and Sentry noise.
                        if (cleanLine.isBlank() || !isImportantAwgLog(cleanLine)) return@forEach

                        val now = System.currentTimeMillis()

                        // Suppress exact duplicate lines within the deduplication window.
                        if (cleanLine == lastLine && now - lastLineEmittedAt < deduplicationWindowMs) {
                            return@forEach
                        }
                        lastLine = cleanLine
                        lastLineEmittedAt = now

                        // Add as breadcrumb (will be sent IF a crash/error happens later).
                        // Note: we deliberately do NOT call ProtonLogger.d() here because that
                        // would trigger addSentryLog() — an extra Sentry SDK IPC call per line
                        // that is unnecessary for routine tunnel noise and adds significant cost.
                        ProtonLogger.addSentryBreadcrumb(
                            "AmneziaWG",
                            cleanLine,
                            SentryLevel.DEBUG,
                            "vpn.awg"
                        )

                        // Local logcat output (debug builds only, no Sentry overhead)
                        if (android.util.Log.isLoggable("Tun/proton_awg", android.util.Log.DEBUG)) {
                            android.util.Log.d("Tun/proton_awg", cleanLine)
                        }

                        // Yield to the coroutine dispatcher so this hot loop does not
                        // monopolise a DefaultDispatcher worker thread and starve the UI.
                        kotlinx.coroutines.yield()
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.e(TAG, "Failed to read tunnel logs from Logcat", e)
            } finally {
                process.destroy()
            }
        }
    }

    /**
     * Filters AWG logs to keep only actionable/important events.
     */
    private fun isImportantAwgLog(msg: String): Boolean {
        val importantKeywords = listOf(
            "handshake",
            "keepalive",
            "keypair",
            "cookie",
            "Rekeying",
            "Retrying",
            "Receiving",
            "Sending"
        )
        return importantKeywords.any { msg.contains(it, ignoreCase = true) }
    }

    private fun stopLogcatCollection() {
        ProtonLogger.d(TAG, "Stopping Logcat collection")
        logcatJob?.cancel()
        logcatJob = null
    }

    /**
     * Formats bytes into a human-readable speed string.
     */
    private fun formatSpeed(bytesPerSec: Long): String {
        return formatBytes(bytesPerSec, true)
    }

    /**
     * Formats bytes into a human-readable size or speed string.
     */
    private fun formatBytes(bytes: Long, isSpeed: Boolean): String {
        val b = maxOf(0.0, bytes.toDouble())
        if (b <= 0.0) return "0 ${getString(if (isSpeed) R.string.unit_b_s else R.string.unit_b)}"
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        val unitRes = when {
            b >= gib -> if (isSpeed) R.string.unit_gb_s else R.string.unit_gb
            b >= mib -> if (isSpeed) R.string.unit_mb_s else R.string.unit_mb
            b >= kib -> if (isSpeed) R.string.unit_kb_s else R.string.unit_kb
            else -> if (isSpeed) R.string.unit_b_s else R.string.unit_b
        }
        val value = when {
            b >= gib -> b / gib
            b >= mib -> b / mib
            b >= kib -> b / kib
            else -> b
        }
        val format = when {
            b >= mib -> "%.2f %s"
            b >= kib -> "%.1f %s"
            else -> "%.0f %s"
        }
        return String.format(Locale.US, format, value, getString(unitRes))
    }

    /**
     * Builds the notification object based on the current VPN state.
     */
    private fun createNotification(stateName: String, speedText: String? = null): Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: "Proton VPN"

        val title = when {
            stateName == Tunnel.State.UP.name && isVerified -> getString(R.string.notification_title_connected, serverName)
            stateName == Tunnel.State.UP.name && !isVerified -> getString(R.string.notification_title_verifying)
            stateName == STATE_CONNECTING -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }

        // Get or create cached PendingIntent for disconnect action
        // Caching reduces IPC calls to system service and prevents DeadSystemException
        val disconnectPendingIntent = try {
            if (cachedDisconnectPendingIntent == null) {
                val disconnectIntent = Intent(this, ProtonVpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                cachedDisconnectPendingIntent = PendingIntent.getService(
                    this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            cachedDisconnectPendingIntent
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Failed to create disconnect PendingIntent, system service may be unavailable", e)
            null
        }

        // Get or create cached PendingIntent for app launch
        val contentPendingIntent = try {
            if (cachedContentPendingIntent == null) {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    cachedContentPendingIntent = PendingIntent.getActivity(
                        this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
            }
            cachedContentPendingIntent
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Failed to create content PendingIntent, system service may be unavailable", e)
            null
        }

        val activeChannelId = if (notificationsEnabled) CHANNEL_ID else CHANNEL_SILENT_ID

        val builder = NotificationCompat.Builder(this, activeChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(if (notificationsEnabled) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setOngoing(stateName != Tunnel.State.DOWN.name)
            .setShowWhen(false)

        // Set content intent if available
        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        if (stateName == Tunnel.State.UP.name) {
            // Add disconnect action only if PendingIntent was successfully created
            if (disconnectPendingIntent != null) {
                builder.addAction(
                    0,
                    getString(R.string.notification_action_disconnect),
                    disconnectPendingIntent
                )
            }
            if (!speedText.isNullOrEmpty() && notificationsEnabled) {
                builder.setContentText(speedText)
            }
        }

        return builder.build()
    }

    /**
     * Updates the foreground service notification or removes it if appropriate.
     */
    private fun updateNotification(stateName: String, isSpeedUpdateOnly: Boolean = false) {
        val isDown = stateName == Tunnel.State.DOWN.name
        val isConnecting = isCurrentlyConnecting || stateName == STATE_CONNECTING

        // Decide if we should show a foreground notification.
        // It must be shown during connection, and kept alive if kill switch is active.
        // CRITICAL FIX: To prevent ForegroundServiceDidNotStartInTimeException,
        // we MUST always show the notification if the service is starting or active.
        // We use the 'SILENT' channel if the user has disabled VPN notifications.
        val shouldShow = when {
            isConnecting -> true
            isDown -> killSwitchEnabled && !isManualDisconnect
            else -> true // Always show if UP, to satisfy Foreground requirements
        }

        if (shouldShow) {
            val notification = createNotification(stateName, lastSpeedText)

            try {
                if (!isForegroundServiceStarted || !isSpeedUpdateOnly) {
                    // Compat layer to handle Android 14+ Foreground Service types safely
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    isForegroundServiceStarted = true
                } else {
                    // Update existing notification without re-registering the foreground service
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                ProtonLogger.e(TAG, "Failed to start/update foreground service", e)
                // If it's a ForegroundServiceStartNotAllowedException, we can't do much
                // but at least we don't crash. The VPN might still work as a background VpnService
                // or it might be killed soon.
            }
        } else {
            stopForegroundOrService(isDown)
        }
    }

    /**
     * Helper to correctly stop the foreground service.
     */
    private fun stopForegroundOrService(stopSelf: Boolean = true) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        isForegroundServiceStarted = false

        if (stopSelf) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        ProtonLogger.d(TAG, "VPN Service destroyed")
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {
            ProtonLogger.w(TAG, "Receiver already unregistered", e)
        }

        // Cancel all ongoing coroutines (like stats job)
        serviceScope.cancel()

        // Ensure the tunnel is cleanly shut down
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Error stopping VPN on service destroy", e)
        }
        super.onDestroy()
    }
}