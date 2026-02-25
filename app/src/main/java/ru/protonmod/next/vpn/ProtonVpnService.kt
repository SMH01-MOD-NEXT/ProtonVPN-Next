package ru.protonmod.next.vpn

import android.content.Intent
import android.system.Os
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

/**
 * Service implementation for AmneziaWG tunnel.
 * Runs in isolated :vpn process to prevent Go runtime clashes with SRP.
 */
@AndroidEntryPoint
class ProtonVpnService : GoBackend.VpnService() {

    companion object {
        private const val TAG = "ProtonVpnService"
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "config_string"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val TUNNEL_NAME = "proton_awg"
    }

    private lateinit var backend: GoBackend

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "State changed to $newState")
            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, newState.name)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)
        }
    }

    override fun onCreate() {
        Log.d(TAG, "VPN Service creating in isolated :vpn process")

        Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        Os.setenv("WG_TUN_DIR", cacheDir.absolutePath, true)

        super.onCreate()

        backend = GoBackend(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service start command received: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                val configStr = intent.getStringExtra(EXTRA_CONFIG)
                if (configStr != null) {
                    try {
                        val configStream = ByteArrayInputStream(configStr.toByteArray())
                        val config = Config.parse(configStream)
                        backend.setState(tunnel, Tunnel.State.UP, config)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start tunnel", e)
                        tunnel.onStateChange(Tunnel.State.DOWN)
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

    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroyed")
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}