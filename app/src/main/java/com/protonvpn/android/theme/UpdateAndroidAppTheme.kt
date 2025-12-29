/*
 * Copyright (c) 2025. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateAndroidAppTheme @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val effectiveCurrentUserSettings: dagger.Lazy<EffectiveCurrentUserSettings>,
    private val effectiveCurrentUserSettingsCached: dagger.Lazy<EffectiveCurrentUserSettingsCached>,
) {

    private interface Delegate {
        fun effectiveSettingsFlow(): Flow<LocalUserSettings>
        fun setAndroidNightMode(theme: ThemeType)
    }

    fun start() {
        val delegate = if (Build.VERSION.SDK_INT < 31) {
            DelegateImplCompat(effectiveCurrentUserSettingsCached.get())
        } else {
            DelegateImpl31(appContext, effectiveCurrentUserSettings.get())
        }

        delegate.effectiveSettingsFlow()
            .map { it.theme }
            .distinctUntilChanged()
            .onEach { theme ->
                delegate.setAndroidNightMode(theme)
                saveThemeToStorage(theme)
            }
            .launchIn(mainScope)
    }

    private fun saveThemeToStorage(theme: ThemeType) {
        try {
            val prefs = appContext.getSharedPreferences("Storage", Context.MODE_PRIVATE)
            prefs.edit().putString("theme", theme.name).apply()
        } catch (e: Exception) { }
    }

    private class DelegateImplCompat(
        private val effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached
    ) : Delegate {
        override fun effectiveSettingsFlow(): Flow<LocalUserSettings> = effectiveCurrentUserSettingsCached

        override fun setAndroidNightMode(theme: ThemeType) {
            val nightMode = when(theme) {
                ThemeType.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                // Светлые темы
                ThemeType.Light, -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeType.NewYearLight -> AppCompatDelegate.MODE_NIGHT_NO

                // Темные темы
                ThemeType.Dark, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.Amoled, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.Gold, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.NewYearAmoled -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    @RequiresApi(31)
    private class DelegateImpl31(
        private val appContext: Context,
        private val effectiveCurrentUserSettings: EffectiveCurrentUserSettings
    ) : Delegate {
        override fun effectiveSettingsFlow(): Flow<LocalUserSettings> = effectiveCurrentUserSettings.effectiveSettings

        override fun setAndroidNightMode(theme: ThemeType) {
            val nightMode = when(theme) {
                ThemeType.System -> UiModeManager.MODE_NIGHT_AUTO

                // Светлые темы
                ThemeType.Light, -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeType.NewYearLight -> UiModeManager.MODE_NIGHT_NO

                // Темные темы
                ThemeType.Dark, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.Amoled, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.Gold, -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeType.NewYearAmoled -> UiModeManager.MODE_NIGHT_YES
            }
            val uiModeManager = appContext.getSystemService<UiModeManager>(UiModeManager::class.java)
            uiModeManager.setApplicationNightMode(nightMode)
        }
    }
}