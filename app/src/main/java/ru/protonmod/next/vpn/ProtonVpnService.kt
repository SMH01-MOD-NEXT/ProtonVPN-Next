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

import ru.protonmod.next.netshield.LocalNetShield
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

internal fun isAwgHandshakeSuccess(message: String): Boolean {
    val normalized = message.lowercase(Locale.ROOT)
    return "received handshake response" in normalized ||
        "handshake response received" in normalized
}

internal fun isAwgHandshakeAttempt(message: String): Boolean =
    "sending handshake initiation" in message.lowercase(Locale.ROOT)

/**
 * Android VPN service backed by amnezia-box (sing-box + AWG/AWG2).
 *
 * The service intentionally keeps the public Intent/broadcast contract stable so the rest of the
 * app can migrate independently from the old wg-quick/GoBackend implementation.
 */
@AndroidEntryPoint
class ProtonVpnService : VpnService(), CommandServerHandler {
    @Inject lateinit var connectedServerState: ConnectedServerState
    @Inject lateinit var localNetShield: LocalNetShield
    @Inject lateinit var vpnNetworkMonitor: VpnNetworkMonitor

    companion object {
        private const val TAG = "ProtonVpnService"
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val ACTION_UPDATE_SETTINGS = "ru.protonmod.next.vpn.UPDATE_SETTINGS"
        const val ACTION_STATS_UPDATED = "ru.protonmod.next.vpn.STATS_UPDATED"
        const val ACTION_SET_VERIFIED = "ru.protonmod.next.vpn.SET_VERIFIED"
        const val ACTION_QUERY_STATE = "ru.protonmod.next.vpn.QUERY_STATE"

        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val EXTRA_SPLIT_TUNNELING_ENABLED = "split_tunneling_enabled"
        const val EXTRA_SPLIT_TUNNELING_MODE = "split_tunneling_mode"
        const val EXTRA_STATE = "state"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TRAFFIC_RX = "traffic_rx"
        const val EXTRA_TRAFFIC_TX = "traffic_tx"
        const val EXTRA_TRAFFIC_DELTA_RX = "traffic_delta_rx"
        const val EXTRA_TRAFFIC_DELTA_TX = "traffic_delta_tx"
        const val EXTRA_TRAFFIC_DELTA_SECONDS = "traffic_delta_seconds"
        const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val EXTRA_KILL_SWITCH_ENABLED = "kill_switch_enabled"
        const val EXTRA_NON_FATAL_ENABLED = "non_fatal_enabled"
        const val EXTRA_ANALYTICS_ENABLED = "analytics_enabled"
        const val EXTRA_LOGICAL_SERVER_ID = "logical_server_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_IS_RECONNECTING = "is_reconnecting"
        const val EXTRA_VERIFIED = "verified"
        const val EXTRA_VERIFICATION_MODE = "verification_mode"
        const val EXTRA_VERIFICATION_REQUIRED = "verification_required"
        const val EXTRA_HANDSHAKE_TIMEOUT_SECONDS = "handshake_timeout_seconds"
        const val EXTRA_FAILURE_DETECTION_ENABLED = "failure_detection_enabled"
        const val EXTRA_AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
        const val STATE_CONNECTING = "CONNECTING"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val CHANNEL_SILENT_ID = "vpn_status_channel_silent"
        private const val FULL_CONFIG_LOG_TAG = "ProtonVpnConfig"
        private const val CRASH_REPORT_SOURCE = "ProtonVpnService"
        private const val LOGCAT_CHUNK_SIZE = 3_500
        private val libboxInitialized = AtomicBoolean(false)

        internal fun shouldShowNotification(stateName: String, enabled: Boolean): Boolean {
            return enabled && stateName != VpnTunnelState.DOWN.name
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineMutex = Mutex()
    private lateinit var platform: AwgBoxPlatform
    private var commandServer: CommandServer? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var reconnectJob: Job? = null
    private var handshakeVerificationJob: Job? = null
    private var engineJob: Job? = null
    private var shutdownJob: Job? = null
    private val lifecycleGeneration = AtomicLong(0)
    private val closingCommandServer = AtomicBoolean(false)
    private val closedCommandServers = Collections.newSetFromMap(
        IdentityHashMap<CommandServer, Boolean>()
    )
    @Volatile private var startingCommandServer: CommandServer? = null
    private var foregroundStarted = false
    private var state = VpnTunnelState.DOWN
    private var connecting = false
    private var verified = false
    private var manualDisconnect = false
    private var notificationsEnabled = true
    private var killSwitchEnabled = false
    private var verificationMode = ConnectionVerificationMode.BALANCED
    private var verificationRequired = false
    private var handshakeTimeoutSeconds = SettingsManager.DEFAULT_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS
    private var failureDetectionEnabled = true
    private var autoReconnectEnabled = true
    private var logicalServerId: String? = null
    private var lastConfig: String? = null
    private var lastConnectIntent: Intent? = null
    @Volatile private var handshakeObserved = false
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastSpeed: String? = null
    private var transportFailureCount = 0
    private var lastTransportFailureAt = 0L
    private var lastHealthReconnectAt = 0L

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DISCONNECT -> stopTunnel(manual = true)
                ACTION_UPDATE_SETTINGS -> applySettings(intent)
                ACTION_SET_VERIFIED -> markVerified()
                ACTION_QUERY_STATE -> sendState(if (connecting) null else state)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeLibbox()
        platform = AwgBoxPlatform(this, vpnNetworkMonitor) { descriptor ->
            tunDescriptor?.close()
            tunDescriptor = descriptor
        }
        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            IntentFilter().apply {
                addAction(ACTION_DISCONNECT)
                addAction(ACTION_UPDATE_SETTINGS)
                addAction(ACTION_SET_VERIFIED)
                addAction(ACTION_QUERY_STATE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ProtonLogger.i(TAG, "amnezia-box ${Libbox.version()} initialized")
    }

    private fun initializeLibbox() {
        if (!libboxInitialized.compareAndSet(false, true)) return
        val workingDir = getExternalFilesDir(null)?.takeIf { it.exists() || it.mkdirs() } ?: filesDir
        val options = SetupOptions().apply {
            basePath = filesDir.absolutePath
            workingPath = workingDir.absolutePath
            tempPath = cacheDir.absolutePath
            logMaxLines = 2_000
            // libbox only invokes CommandServerHandler.writeDebugMessage when this flag is on.
            // NetShield counts DNS rule matches through that callback in every build type; raw
            // engine messages are still written to Logcat only in debug builds below.
            debug = true
            fixAndroidStack = true
            // Libbox.setup() now owns the stderr redirect that Libbox.redirectStderr() used to
            // perform; it writes native panics to "CrashReport-$crashReportSource.log" in
            // workingPath.
            crashReportSource = CRASH_REPORT_SOURCE
        }
        Libbox.setup(options)
        Libbox.setLocale(Locale.getDefault().toLanguageTag().replace('-', '_'))
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startTunnel(intent)
            ACTION_DISCONNECT -> stopTunnel(manual = true)
            ACTION_UPDATE_SETTINGS -> applySettings(intent)
            ACTION_SET_VERIFIED -> markVerified()
            ACTION_QUERY_STATE -> sendState(if (connecting) null else state)
            else -> return START_NOT_STICKY
        }
        return if (state == VpnTunnelState.DOWN && !connecting) START_NOT_STICKY else START_STICKY
    }

    private fun markVerified() {
        if (verified || state != VpnTunnelState.UP) return
        verified = true
        connecting = false
        handshakeVerificationJob?.cancel()
        handshakeVerificationJob = null
        sendState(VpnTunnelState.UP)
        updateNotification(VpnTunnelState.UP.name)
    }

    private fun startTunnel(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_IS_RECONNECTING, false)) {
            localNetShield.beginSessionStats()
        }
        val config = intent.getStringExtra(EXTRA_CONFIG) ?: run {
            ProtonLogger.e(TAG, "Missing awgbox configuration")
            return
        }
        logicalServerId = intent.getStringExtra(EXTRA_LOGICAL_SERVER_ID)
        notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
        killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, false)
        readHealthSettings(intent)
        platform.configureSplitTunneling(
            enabled = intent.getBooleanExtra(EXTRA_SPLIT_TUNNELING_ENABLED, false),
            mode = intent.getStringExtra(EXTRA_SPLIT_TUNNELING_MODE) ?: "exclude",
            selectedApps = intent.getStringArrayListExtra(EXTRA_EXCLUDED_APPS).orEmpty().toSet()
        )
        lastConfig = config
        lastConnectIntent = Intent(intent).apply {
            setClass(this@ProtonVpnService, ProtonVpnService::class.java)
            action = ACTION_CONNECT
        }
        logFullConfigToLogcat(config)
        manualDisconnect = false
        val generation = lifecycleGeneration.incrementAndGet()
        handshakeVerificationJob?.cancel()
        handshakeVerificationJob = null
        handshakeObserved = false
        verified = verificationMode == ConnectionVerificationMode.DISABLED ||
            (!verificationMode.handshakeOnly && !verificationRequired)
        connecting = true
        updateNotification(STATE_CONNECTING, ensureForeground = true)
        sendState(null)

        reconnectJob?.cancel()
        engineJob?.cancel()
        val pendingShutdown = shutdownJob
        engineJob = scope.launch(Dispatchers.IO) {
            try {
                pendingShutdown?.join()
                currentCoroutineContext().ensureActive()
                if (lifecycleGeneration.get() != generation) return@launch

                engineMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (lifecycleGeneration.get() != generation) return@withLock
                    closeEngine()

                    val server = CommandServer(this@ProtonVpnService, platform).also { it.start() }
                    startingCommandServer = server
                    var adopted = false
                    try {
                        Libbox.checkConfig(config)
                        server.startOrReloadService(config, OverrideOptions())
                        currentCoroutineContext().ensureActive()
                        if (lifecycleGeneration.get() != generation) return@withLock

                        commandServer = server
                        startingCommandServer = null
                        adopted = true
                        withContext(Dispatchers.Main) {
                            if (lifecycleGeneration.get() != generation || !connecting) return@withContext
                            state = VpnTunnelState.UP
                            connecting = false
                            resetTransportFailures()
                            sendState(VpnTunnelState.UP)
                            updateNotification(VpnTunnelState.UP.name)
                            startTrafficUpdates()
                            if (verificationMode.handshakeOnly) {
                                if (handshakeObserved) markVerified()
                                else startHandshakeVerificationWatchdog(generation)
                            }
                        }
                    } finally {
                        if (!adopted) closeCommandServer(server)
                        if (startingCommandServer === server) startingCommandServer = null
                    }
                }
            } catch (_: CancellationException) {
                // A newer connect or disconnect owns the lifecycle now.
            } catch (error: Exception) {
                if (lifecycleGeneration.get() != generation) return@launch
                if (hasUsableUnderlyingNetwork()) {
                    ProtonLogger.e(TAG, "Failed to start amnezia-box tunnel", error)
                } else {
                    // The device lost connectivity between the preflight and the engine start, so
                    // the engine cannot bind an outbound socket. Transient, and not a defect.
                    ProtonLogger.w(TAG, "Tunnel start aborted without connectivity: ${error.message}")
                }
                withContext(Dispatchers.Main) {
                    if (lifecycleGeneration.get() == generation) handleEngineFailure()
                }
            }
        }
    }

    /**
     * Emits the exact generated sing-box configuration to local Logcat only.
     *
     * This deliberately bypasses ProtonLogger so the private key, proxy UUIDs and other
     * credentials can never become Sentry breadcrumbs or Sentry logs. Full configuration
     * logging is restricted to debug builds because Logcat is not an appropriate secret store.
     */
    private fun logFullConfigToLogcat(config: String) {
        if (!BuildConfig.DEBUG) return

        val chunks = config.chunked(LOGCAT_CHUNK_SIZE).ifEmpty { listOf("") }
        Log.d(FULL_CONFIG_LOG_TAG, "----- BEGIN AWGBOX CONFIG (${config.length} chars) -----")
        chunks.forEachIndexed { index, chunk ->
            Log.d(FULL_CONFIG_LOG_TAG, "[${index + 1}/${chunks.size}] $chunk")
        }
        Log.d(FULL_CONFIG_LOG_TAG, "----- END AWGBOX CONFIG -----")
    }

    /**
     * True when a non-VPN network that claims internet access is still present. Used to tell an
     * engine start failure caused by the device going offline apart from a real configuration or
     * runtime defect.
     */
    private fun hasUsableUnderlyingNetwork(): Boolean {
        val networks = vpnNetworkMonitor.getTrackedNetworks()
        if (networks.isEmpty()) return true
        return networks.any { tracked ->
            val capabilities = tracked.capabilities ?: return@any false
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }

    private fun handleEngineFailure() {
        state = VpnTunnelState.DOWN
        connecting = false
        verified = false
        sendState(VpnTunnelState.DOWN)
        updateNotification(VpnTunnelState.DOWN.name)
        if (killSwitchEnabled && autoReconnectEnabled && !manualDisconnect && !lastConfig.isNullOrBlank()) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(3.seconds)
                val retry = Intent(this@ProtonVpnService, ProtonVpnService::class.java).apply {
                    action = ACTION_CONNECT
                    putExtra(EXTRA_CONFIG, lastConfig)
                    putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
                    putExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                    putExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
                    putExtra(EXTRA_IS_RECONNECTING, true)
                    putExtra(EXTRA_VERIFICATION_MODE, verificationMode.name)
                    putExtra(EXTRA_VERIFICATION_REQUIRED, verificationRequired)
                    putExtra(EXTRA_FAILURE_DETECTION_ENABLED, failureDetectionEnabled)
                    putExtra(EXTRA_AUTO_RECONNECT_ENABLED, autoReconnectEnabled)
                }
                startTunnel(retry)
            }
        } else {
            scope.launch(Dispatchers.IO) { localNetShield.finishSessionStats() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTunnel(manual: Boolean) {
        manualDisconnect = manual
        val generation = lifecycleGeneration.incrementAndGet()
        reconnectJob?.cancel()
        handshakeVerificationJob?.cancel()
        handshakeVerificationJob = null
        engineJob?.cancel()
        connecting = false
        verified = false
        state = VpnTunnelState.DOWN
        sendState(VpnTunnelState.DOWN)
        stopTrafficUpdates()
        updateNotification(VpnTunnelState.DOWN.name)

        val previousShutdown = shutdownJob
        shutdownJob = scope.launch(Dispatchers.IO) {
            // libbox StartOrReloadService is blocking and CloseService is not safe to call
            // concurrently with it. Cancellation marks the attempt stale; after native startup
            // returns, that same engine coroutine closes its candidate before releasing the mutex.
            previousShutdown?.join()
            localNetShield.finishSessionStats()
            engineMutex.withLock { closeEngine() }
            withContext(Dispatchers.Main) {
                // A new connect may already be waiting for this shutdown to finish. Do not
                // stop the service underneath that connection attempt.
                if (lifecycleGeneration.get() == generation &&
                    (manual || !killSwitchEnabled) && !connecting
                ) {
                    stopSelf()
                }
            }
        }
    }

    private fun closeEngine() {
        val server = commandServer
        commandServer = null
        closeCommandServer(server)
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    @Synchronized
    private fun closeCommandServer(server: CommandServer?) {
        if (server == null || !closedCommandServers.add(server)) return
        closingCommandServer.set(true)
        try {
            runCatching { server.closeService() }
            runCatching { server.close() }
        } finally {
            closingCommandServer.set(false)
        }
    }

    private fun applySettings(intent: Intent) {
        notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
        killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
        if (intent.hasExtra(EXTRA_NON_FATAL_ENABLED)) {
            ProtonLogger.isNonFatalEnabled = intent.getBooleanExtra(EXTRA_NON_FATAL_ENABLED, true)
        }
        if (intent.hasExtra(EXTRA_ANALYTICS_ENABLED)) {
            ProtonLogger.isAnalyticsEnabled = intent.getBooleanExtra(EXTRA_ANALYTICS_ENABLED, true)
        }
        readHealthSettings(intent)
        if (verificationMode.handshakeOnly && state == VpnTunnelState.UP && !verified) {
            startHandshakeVerificationWatchdog(lifecycleGeneration.get())
        }
        updateNotification(if (connecting) STATE_CONNECTING else state.name)
    }

    private fun readHealthSettings(intent: Intent) {
        if (intent.hasExtra(EXTRA_VERIFICATION_MODE)) {
            verificationMode = runCatching {
                ConnectionVerificationMode.valueOf(intent.getStringExtra(EXTRA_VERIFICATION_MODE).orEmpty())
            }.getOrDefault(ConnectionVerificationMode.BALANCED)
        }
        verificationRequired = intent.getBooleanExtra(EXTRA_VERIFICATION_REQUIRED, verificationRequired)
        handshakeTimeoutSeconds = intent.getIntExtra(EXTRA_HANDSHAKE_TIMEOUT_SECONDS, handshakeTimeoutSeconds)
            .coerceIn(
                SettingsManager.MIN_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS,
                SettingsManager.MAX_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS,
            )
        failureDetectionEnabled = intent.getBooleanExtra(EXTRA_FAILURE_DETECTION_ENABLED, failureDetectionEnabled)
        autoReconnectEnabled = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT_ENABLED, autoReconnectEnabled)
        if (!failureDetectionEnabled || verificationMode == ConnectionVerificationMode.DISABLED) {
            resetTransportFailures()
        }
    }

    private fun sendState(explicitState: VpnTunnelState?) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, explicitState?.name ?: STATE_CONNECTING)
            putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
            putExtra(EXTRA_IS_RECONNECTING, reconnectJob?.isActive == true)
            putExtra(EXTRA_VERIFIED, verified)
            setPackage(packageName)
        })
    }

    private fun startTrafficUpdates() {
        stopTrafficUpdates()
        val uid = applicationInfo.uid
        lastRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        lastTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1.seconds)
                val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(lastRx)
                val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(lastTx)
                val deltaRx = rx - lastRx
                val deltaTx = tx - lastTx
                lastRx = rx
                lastTx = tx
                lastSpeed = getString(R.string.vpn_speed_format, formatBytes(deltaTx, true), formatBytes(deltaRx, true))
                sendBroadcast(Intent(ACTION_STATS_UPDATED).apply {
                    putExtra(EXTRA_SPEED, lastSpeed)
                    putExtra(EXTRA_TRAFFIC_RX, formatBytes(rx, false))
                    putExtra(EXTRA_TRAFFIC_TX, formatBytes(tx, false))
                    putExtra(EXTRA_TRAFFIC_DELTA_RX, deltaRx)
                    putExtra(EXTRA_TRAFFIC_DELTA_TX, deltaTx)
                    putExtra(EXTRA_TRAFFIC_DELTA_SECONDS, 1L)
                    putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
                    setPackage(packageName)
                })
                if (state == VpnTunnelState.UP) updateNotification(state.name)
            }
        }
    }

    private fun stopTrafficUpdates() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun formatBytes(bytes: Long, speed: Boolean): String {
        val value = bytes.coerceAtLeast(0).toDouble()
        val (scaled, unit) = when {
            value >= 1024 * 1024 * 1024 -> value / (1024 * 1024 * 1024) to if (speed) R.string.unit_gb_s else R.string.unit_gb
            value >= 1024 * 1024 -> value / (1024 * 1024) to if (speed) R.string.unit_mb_s else R.string.unit_mb
            value >= 1024 -> value / 1024 to if (speed) R.string.unit_kb_s else R.string.unit_kb
            else -> value to if (speed) R.string.unit_b_s else R.string.unit_b
        }
        return String.format(Locale.US, if (scaled >= 1024) "%.0f %s" else "%.1f %s", scaled, getString(unit))
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val name = getString(R.string.notification_channel_name)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SILENT_ID, getString(R.string.notification_channel_silent_name), NotificationManager.IMPORTANCE_MIN))
    }

    private fun createNotification(stateName: String): Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: getString(R.string.app_name)
        val title = when {
            stateName == VpnTunnelState.UP.name && verified -> getString(R.string.notification_title_connected, serverName)
            stateName == VpnTunnelState.UP.name -> getString(R.string.notification_title_verifying)
            stateName == STATE_CONNECTING -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProtonVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, if (notificationsEnabled) CHANNEL_ID else CHANNEL_SILENT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lastSpeed)
            .setContentIntent(contentIntent)
            .setOngoing(stateName != VpnTunnelState.DOWN.name)
            .setShowWhen(false)
            .addAction(0, getString(R.string.notification_action_disconnect), disconnectIntent)
            .build()
    }

    private fun updateNotification(stateName: String, ensureForeground: Boolean = false) {
        if (!shouldShowNotification(stateName, notificationsEnabled)) {
            // startForegroundService() still requires one foreground promotion. Satisfy it for
            // a disabled notification setting, then remove the notification completely.
            if (ensureForeground && !foregroundStarted) {
                startForegroundNotification(createNotification(stateName))
            }
            removeNotification()
            return
        }
        startForegroundNotification(createNotification(stateName))
    }

    private fun startForegroundNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun removeNotification() {
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    override fun serviceStop() {
        if (closingCommandServer.get()) return
        scope.launch {
            if (!closingCommandServer.get()) stopTunnel(manual = false)
        }
    }
    override fun serviceReload() = Unit
    override fun getSystemProxyStatus() = SystemProxyStatus().apply { available = false; enabled = false }
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit

    // Only reachable through Tailscale SSH agent forwarding, which this AAR does not build.
    override fun connectSSHAgent(): Int =
        throw UnsupportedOperationException("SSH agent forwarding is not supported")

    // A debug-only command from the sing-box GUI clients; deliberately never honoured here.
    override fun triggerNativeCrash(): Unit =
        throw UnsupportedOperationException("Native crash trigger is disabled")

    override fun writeDebugMessage(message: String?) {
        val logMessage = message.orEmpty()
        if (BuildConfig.DEBUG) {
            ProtonLogger.d("awgbox", logMessage)
        }
        localNetShield.recordEngineLog(logMessage)
        observeHandshake(logMessage)
        observeTransportHealth(logMessage)
    }

    private fun observeHandshake(message: String) {
        if (!verificationMode.handshakeOnly) return
        when {
            isAwgHandshakeSuccess(message) -> {
                handshakeObserved = true
                scope.launch {
                    if (verificationMode.handshakeOnly && state == VpnTunnelState.UP && !verified) {
                        ProtonLogger.i(TAG, "AmneziaWG handshake confirmed")
                        markVerified()
                    }
                }
            }
            isAwgHandshakeAttempt(message) && state == VpnTunnelState.UP && verified -> {
                scope.launch {
                    if (!verificationMode.handshakeOnly || state != VpnTunnelState.UP || !verified) return@launch
                    ProtonLogger.w(TAG, "AmneziaWG started a new handshake; opening verification window")
                    handshakeObserved = false
                    verified = false
                    sendState(VpnTunnelState.UP)
                    updateNotification(VpnTunnelState.UP.name)
                    startHandshakeVerificationWatchdog(lifecycleGeneration.get())
                }
            }
        }
    }

    private fun startHandshakeVerificationWatchdog(generation: Long) {
        handshakeVerificationJob?.cancel()
        handshakeVerificationJob = scope.launch {
            delay(handshakeTimeoutSeconds.toLong().seconds)
            if (lifecycleGeneration.get() != generation || verified || manualDisconnect ||
                !verificationMode.handshakeOnly || state != VpnTunnelState.UP
            ) return@launch
            ProtonLogger.w(TAG, "No AmneziaWG handshake in $handshakeTimeoutSeconds seconds; reconnecting to the same server")
            restartTunnelAfterHandshakeTimeout()
        }
    }

    private fun restartTunnelAfterHandshakeTimeout() {
        val retry = lastConnectIntent?.let(::Intent) ?: return
        retry.putExtra(EXTRA_IS_RECONNECTING, true)
        startTunnel(retry)
    }

    private fun observeTransportHealth(message: String) {
        if (verificationMode.handshakeOnly || !failureDetectionEnabled ||
            verificationMode == ConnectionVerificationMode.DISABLED
        ) return
        val normalized = message.lowercase(Locale.ROOT)
        when {
            isSuccessfulTransportActivity(normalized) -> scope.launch { resetTransportFailures() }
            isTransportFailure(normalized) -> scope.launch { recordTransportFailure(normalized) }
        }
    }

    private fun isSuccessfulTransportActivity(message: String): Boolean {
        return ("dns: exchanged " in message && "exchange failed" !in message) ||
            "received handshake response" in message
    }

    private fun isTransportFailure(message: String): Boolean {
        val timedOut = "context deadline exceeded" in message ||
            "i/o timeout" in message ||
            "tls handshake timeout" in message
        val transportError = "connection reset by peer" in message ||
            "broken pipe" in message ||
            "network is unreachable" in message
        val relevantPath = "dns: exchange failed" in message ||
            "outbound/vless" in message ||
            "outbound/vmess" in message ||
            "endpoint/awg" in message
        return relevantPath && (timedOut || transportError)
    }

    private fun recordTransportFailure(message: String) {
        if (state != VpnTunnelState.UP || connecting || manualDisconnect) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTransportFailureAt > verificationMode.failureWindowMs) {
            transportFailureCount = 0
        }
        lastTransportFailureAt = now
        transportFailureCount++
        ProtonLogger.w(
            TAG,
            "Tunnel transport health failure $transportFailureCount/${verificationMode.failureThreshold}"
        )

        if (transportFailureCount < verificationMode.failureThreshold) return
        if (!autoReconnectEnabled) return
        if (lastHealthReconnectAt != 0L && now - lastHealthReconnectAt < verificationMode.reconnectCooldownMs) return

        lastHealthReconnectAt = now
        transportFailureCount = 0
        restartTunnelAfterHealthFailure(message)
    }

    private fun restartTunnelAfterHealthFailure(reason: String) {
        val config = lastConfig ?: return
        if (connecting || manualDisconnect) return

        ProtonLogger.w(TAG, "Tunnel transport is unresponsive; reconnecting")
        ProtonLogger.addSentryBreadcrumb(
            TAG,
            "Automatic reconnect after transport health failure: ${reason.take(160)}",
            "WARNING",
            "vpn.health"
        )
        val retry = Intent(this, ProtonVpnService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_CONFIG, config)
            putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
            putExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
            putExtra(EXTRA_IS_RECONNECTING, true)
        }
        startTunnel(retry)
    }

    private fun resetTransportFailures() {
        transportFailureCount = 0
        lastTransportFailureAt = 0L
    }

    override fun onRevoke() {
        stopTunnel(manual = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(settingsReceiver) }
        lifecycleGeneration.incrementAndGet()
        reconnectJob?.cancel()
        handshakeVerificationJob?.cancel()
        handshakeVerificationJob = null
        engineJob?.cancel()
        shutdownJob?.cancel()
        // Never race CloseService against blocking StartOrReloadService; process teardown will
        // release a still-starting native candidate.
        startingCommandServer = null
        stopTrafficUpdates()
        removeNotification()
        closeEngine()
        scope.cancel()
        super.onDestroy()
    }
}
