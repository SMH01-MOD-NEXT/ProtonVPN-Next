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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemContextWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startVpnService(
        configStr: String,
        notificationsEnabled: Boolean,
        killSwitchEnabled: Boolean,
        excludedApps: Set<String>,
        excludedIps: Set<String>
    ) {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_CONNECT
            putExtra(ProtonVpnService.EXTRA_CONFIG, configStr)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
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

    fun updateVpnSettings(notificationsEnabled: Boolean, killSwitchEnabled: Boolean, nonFatalEnabled: Boolean, analyticsEnabled: Boolean) {
        val intent = Intent(ProtonVpnService.ACTION_UPDATE_SETTINGS).apply {
            setPackage(context.packageName)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
            putExtra(ProtonVpnService.EXTRA_NON_FATAL_ENABLED, nonFatalEnabled)
            putExtra(ProtonVpnService.EXTRA_ANALYTICS_ENABLED, analyticsEnabled)
        }
        context.sendBroadcast(intent)
    }

    fun setVpnVerified() {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_SET_VERIFIED
        }
        context.startService(intent)
    }
}
