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

package ru.protonmod.next.utils.system

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ru.protonmod.next.vpn.ProtonVpnService
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.utils.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemContextWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startVpnService(
        configStr: String,
        logicalServerId: String?,
        sessionId: Long,
        notificationsEnabled: Boolean,
        killSwitchEnabled: Boolean,
        verificationMode: ConnectionVerificationMode,
        verificationRequired: Boolean,
        failureDetectionEnabled: Boolean,
        autoReconnectEnabled: Boolean,
        splitTunnelingEnabled: Boolean,
        splitTunnelingMode: String,
        excludedApps: Set<String>,
        excludedIps: Set<String>
    ) {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_CONNECT
            putExtra(ProtonVpnService.EXTRA_CONFIG, configStr)
            putExtra(ProtonVpnService.EXTRA_LOGICAL_SERVER_ID, logicalServerId)
            putExtra(ProtonVpnService.EXTRA_SESSION_ID, sessionId)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
            putExtra(ProtonVpnService.EXTRA_VERIFICATION_MODE, verificationMode.name)
            putExtra(ProtonVpnService.EXTRA_VERIFICATION_REQUIRED, verificationRequired)
            putExtra(ProtonVpnService.EXTRA_FAILURE_DETECTION_ENABLED, failureDetectionEnabled)
            putExtra(ProtonVpnService.EXTRA_AUTO_RECONNECT_ENABLED, autoReconnectEnabled)
            putExtra(ProtonVpnService.EXTRA_SPLIT_TUNNELING_ENABLED, splitTunnelingEnabled)
            putExtra(ProtonVpnService.EXTRA_SPLIT_TUNNELING_MODE, splitTunnelingMode)
            putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_APPS, ArrayList(excludedApps))
            putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_IPS, ArrayList(excludedIps))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpnService() {
        // Use a broadcast instead of startService() to avoid BackgroundServiceStartNotAllowedException
        // on Android 8+ when the app is in the background. The service's broadcast receiver handles
        // ACTION_DISCONNECT to set isManualDisconnect and tear down the tunnel gracefully.
        val intent = Intent(ProtonVpnService.ACTION_DISCONNECT).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun updateVpnSettings(
        notificationsEnabled: Boolean,
        killSwitchEnabled: Boolean,
        nonFatalEnabled: Boolean,
        analyticsEnabled: Boolean,
        verificationMode: ConnectionVerificationMode,
        verificationRequired: Boolean,
        failureDetectionEnabled: Boolean,
        autoReconnectEnabled: Boolean,
    ) {
        val intent = Intent(ProtonVpnService.ACTION_UPDATE_SETTINGS).apply {
            setPackage(context.packageName)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
            putExtra(ProtonVpnService.EXTRA_NON_FATAL_ENABLED, nonFatalEnabled)
            putExtra(ProtonVpnService.EXTRA_ANALYTICS_ENABLED, analyticsEnabled)
            putExtra(ProtonVpnService.EXTRA_VERIFICATION_MODE, verificationMode.name)
            putExtra(ProtonVpnService.EXTRA_VERIFICATION_REQUIRED, verificationRequired)
            putExtra(ProtonVpnService.EXTRA_FAILURE_DETECTION_ENABLED, failureDetectionEnabled)
            putExtra(ProtonVpnService.EXTRA_AUTO_RECONNECT_ENABLED, autoReconnectEnabled)
        }
        context.sendBroadcast(intent)
    }

    fun setVpnVerified() {
        // Broadcast rather than startService(): the tunnel must already be up for verification to
        // mean anything, and an explicit service start would throw when the platform refuses to
        // launch the ":vpn" process (see [queryVpnState]).
        val intent = Intent(ProtonVpnService.ACTION_SET_VERIFIED).apply {
            setPackage(context.packageName)
        }
        sendQuietly(intent)
    }

    /**
     * Asks a running tunnel to re-announce its state.
     *
     * This used to call startService(), which launched the ":vpn" process purely to ask it a
     * question. It runs while the Dagger graph is built in Application.onCreate, and the platform
     * refuses a service start there in several situations - notably "process is bad" right after a
     * crash - which took the whole app down before its first frame. A broadcast cannot start the
     * service: when nothing answers, the tunnel is not running and the DOWN default already holds.
     */
    fun queryVpnState() {
        val intent = Intent(ProtonVpnService.ACTION_QUERY_STATE).apply {
            setPackage(context.packageName)
        }
        sendQuietly(intent)
    }

    /**
     * Fire-and-forget delivery. These intents run during app startup, so a platform refusal must
     * never escalate into a crash on a path where there is nothing to recover.
     */
    private fun sendQuietly(intent: Intent) {
        runCatching { context.sendBroadcast(intent) }
            .onFailure { error ->
                ProtonLogger.w(TAG, "Failed to deliver ${intent.action}: ${error.message}")
            }
    }

    private companion object {
        const val TAG = "SystemContextWrapper"
    }
}
