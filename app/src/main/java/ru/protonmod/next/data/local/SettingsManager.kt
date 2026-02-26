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

package ru.protonmod.next.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications")
        
        // Split Tunneling
        private val SPLIT_TUNNELING_ENABLED = booleanPreferencesKey("split_tunneling_enabled")
        private val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val EXCLUDED_IPS = stringSetPreferencesKey("excluded_ips")
    }

    val killSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[KILL_SWITCH] ?: false }
    val autoConnectEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS] ?: true }
    
    val splitTunnelingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_TUNNELING_ENABLED] ?: false }
    val excludedApps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_APPS] ?: emptySet() }
    val excludedIps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_IPS] ?: emptySet() }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KILL_SWITCH] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS] = enabled }
    }
    
    suspend fun setSplitTunnelingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SPLIT_TUNNELING_ENABLED] = enabled }
    }
    
    suspend fun setExcludedApps(apps: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_APPS] = apps }
    }
    
    suspend fun setExcludedIps(ips: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_IPS] = ips }
    }
}
