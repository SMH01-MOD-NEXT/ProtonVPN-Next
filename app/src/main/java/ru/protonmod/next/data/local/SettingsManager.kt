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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ru.protonmod.next.ui.screens.settings.ObfuscationProfile
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

        private val SPLIT_TUNNELING_ENABLED = booleanPreferencesKey("split_tunneling_enabled")
        private val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val EXCLUDED_IPS = stringSetPreferencesKey("excluded_ips")

        private val VPN_PORT = intPreferencesKey("vpn_port")

        private val OBFUSCATION_ENABLED = booleanPreferencesKey("obfuscation_enabled")
        private val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles")

        private val AWG_JC = intPreferencesKey("awg_jc")
        private val AWG_JMIN = intPreferencesKey("awg_jmin")
        private val AWG_JMAX = intPreferencesKey("awg_jmax")
        private val AWG_S1 = intPreferencesKey("awg_s1")
        private val AWG_S2 = intPreferencesKey("awg_s2")
        private val AWG_H1 = stringPreferencesKey("awg_h1")
        private val AWG_H2 = stringPreferencesKey("awg_h2")
        private val AWG_H3 = stringPreferencesKey("awg_h3")
        private val AWG_H4 = stringPreferencesKey("awg_h4")
        private val AWG_I1 = stringPreferencesKey("awg_i1")

        const val DEFAULT_I1 = "<b 0xc6000000010843290a47ba8ba2ed000044d0e3efd9326adb60561baa3bc4b52471b2d459ddcc9a508dffddc97e4d40d811d3de7bc98cf06ea85902361ca3ae66b2a99c7de96f0ace4ba4710658aefde6dec6837bc1a48f47bbd63c6e60ff494d3e1bea5f13927922401c40b0f4570d26be6806b506a9ff5f75ca86fae5f8175d4b6bfd418df9b922cdff8e60b06decfe66f2b07da61a47b5c8b32fa999d8feac21c8878b6e15ee03b8388b2afd9ffd3b46753b0284907b10747e526eebf287ff08735929c4c5e4784a5e2ad3dd8ac8200d0e99ad1219e54060ddc72813e8a3e2291ac713c5f3251c5d748fd68782a2e8eb0c021e437a79aafb253efae3ee72e1051b647c45b676d3b9e474d4f60c7bf7d328106cb94f67eaf2c991cd7043371debbf2b4159b8f80f5da0e1b18f4da35fca0a88026b375f1082731d1cbbe9ba3ae2bfefec250ee328ded7f8330d4cda38f74a7fe10b58ace936fc83cfcb3e1ebed520f7559549a8f20568a248e16949611057a3dd9952bae9b7be518c2b5b2568b8582c165c73a6e8f9b042ec9702704a94dd99893421310d43ffc9caf003ff5fc7bcd718f3fa99d663a8bbad6b595ec1d4cf3c0ed1668d0541c4e5b7e5ded40c6628eb64b29f514424d08d8522ddf7b856e9b820441907177a3dbd9b958172173be8c45c8c7b1816fe4d24927f9b12778153fc118194786c6cf49bc5cf09177f73be27917a239592f9acd9a21150abbd1ca93b1e305dc64d9883429a032c3179e0639592c248cbacec00c90bfb5d5eaf8920bf80c47085a490ead8d0af45f6754e8ae5692f86be02c480ca2a1e6156dccf1bcb5c911c05e3c3a946ca23461669c57d287dcfa9dd187fc6a58394f0b2878c07e1d8cb6be41725d49c118e9ddbe1ae6e5d1a04b36ad98a24f0378deea84febb60b22dc81d8377fb3f21069d83e90b9eba2b6b0ea95acf5fd0482a00d064a9b73e0b3732fde45404e22349a514151381fc6095a8204234359c28765e360a57eb222418b11be704651e4da1b52b135d31ba63a7f06a0f7b8b6177f9bd02fb517877a1340e59d8dbe52ea8135bc80b2aa1959539803a31720ac949c7bf0785c2e413e8b83dd4fd40d8a63fbd832ecb727d0c0df04ce10dac6a7d6d75e264aaf856e7485cc2c4e1749f169e5ad4de6f89a2333e362910dd0d962e3bf59a353df5760fd15956fe32e40f863ea020e9709aa9a9ebeffc885e64231dc6fc384bc6a9e7e5c64c0eaf39f9f14a13658883ee8dd94737cb3a8c2f7020bfacb80f0122866635873e248e22b5a5abc84d507e1720d3fb5f827d75c1d39235a361a217eb0587d1639b0b31aef1fe91958220fcf934c2517dea2f1afe51cd63ac31b5f9323a427c36a5442f8a89b7494f1592666f62be0d8cf67fdf5ef38fafc55b7b4f569a105dfa9925f0a41913c6ee13064d4b83f9ee1c3231c402d68a624e2388e357144be99197dcafb92118d9a9ec6fe832771e12448a146fb5b9620a4718070b368aab646b03cce41ec4d5d9a9c880a9cff06aba991cc0845030abbac87c67255f0373eb38444a51d0958e57c7a33042697465c84abe6791cb8f28e484c4cd04f10791ad911b0dcc217f66cb3aa5fcdbb1e2be88139c4ac2652e469122408feba59ad04f66eb8ab8c80aaf10c2ec1f80b5be111d3ccc832df2395a947e335e7908fda5dcdaa14a61f0fa7156c94b1c96e5c191d850e341adc2e22c8f69fcfa5c3e403eadc933f18be3734bc345def4f40ea3e12>"
    }

    val killSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[KILL_SWITCH] ?: false }
    val autoConnectEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS] ?: true }

    val splitTunnelingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_TUNNELING_ENABLED] ?: false }
    val excludedApps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_APPS] ?: emptySet() }
    val excludedIps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_IPS] ?: emptySet() }

    val vpnPort: Flow<Int> = context.dataStore.data.map { it[VPN_PORT] ?: 1194 }

    val obfuscationEnabled: Flow<Boolean> = context.dataStore.data.map { it[OBFUSCATION_ENABLED] ?: false }
    val selectedProfileId: Flow<String> = context.dataStore.data.map { it[SELECTED_PROFILE_ID] ?: "standard_1" }

    val customProfiles: Flow<List<ObfuscationProfile>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[CUSTOM_PROFILES] ?: "[]"
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ObfuscationProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ObfuscationProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        isReadOnly = obj.optBoolean("isReadOnly", false),
                        jc = obj.optInt("jc", 3),
                        jmin = obj.optInt("jmin", 1),
                        jmax = obj.optInt("jmax", 3),
                        s1 = obj.optInt("s1", 0),
                        s2 = obj.optInt("s2", 0),
                        h1 = obj.optString("h1", "1"),
                        h2 = obj.optString("h2", "2"),
                        h3 = obj.optString("h3", "3"),
                        h4 = obj.optString("h4", "4"),
                        i1 = obj.optString("i1", DEFAULT_I1)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val awgJc: Flow<Int> = context.dataStore.data.map { it[AWG_JC] ?: 3 }
    val awgJmin: Flow<Int> = context.dataStore.data.map { it[AWG_JMIN] ?: 1 }
    val awgJmax: Flow<Int> = context.dataStore.data.map { it[AWG_JMAX] ?: 3 }
    val awgS1: Flow<Int> = context.dataStore.data.map { it[AWG_S1] ?: 0 }
    val awgS2: Flow<Int> = context.dataStore.data.map { it[AWG_S2] ?: 0 }
    val awgH1: Flow<String> = context.dataStore.data.map { it[AWG_H1] ?: "1" }
    val awgH2: Flow<String> = context.dataStore.data.map { it[AWG_H2] ?: "2" }
    val awgH3: Flow<String> = context.dataStore.data.map { it[AWG_H3] ?: "3" }
    val awgH4: Flow<String> = context.dataStore.data.map { it[AWG_H4] ?: "4" }
    val awgI1: Flow<String> = context.dataStore.data.map { it[AWG_I1] ?: DEFAULT_I1 }

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

    suspend fun setVpnPort(port: Int) {
        context.dataStore.edit { it[VPN_PORT] = port }
    }

    suspend fun setObfuscationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OBFUSCATION_ENABLED] = enabled }
    }

    suspend fun setSelectedProfileId(id: String) {
        context.dataStore.edit { it[SELECTED_PROFILE_ID] = id }
    }

    suspend fun saveCustomProfiles(profiles: List<ObfuscationProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isReadOnly", p.isReadOnly)
                put("jc", p.jc)
                put("jmin", p.jmin)
                put("jmax", p.jmax)
                put("s1", p.s1)
                put("s2", p.s2)
                put("h1", p.h1)
                put("h2", p.h2)
                put("h3", p.h3)
                put("h4", p.h4)
                put("i1", p.i1)
            }
            array.put(obj)
        }
        context.dataStore.edit { it[CUSTOM_PROFILES] = array.toString() }
    }

    suspend fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int,
        h1: String, h2: String, h3: String, h4: String, i1: String
    ) {
        context.dataStore.edit {
            it[AWG_JC] = jc
            it[AWG_JMIN] = jmin
            it[AWG_JMAX] = jmax
            it[AWG_S1] = s1
            it[AWG_S2] = s2
            it[AWG_H1] = h1
            it[AWG_H2] = h2
            it[AWG_H3] = h3
            it[AWG_H4] = h4
            it[AWG_I1] = i1
        }
    }
}