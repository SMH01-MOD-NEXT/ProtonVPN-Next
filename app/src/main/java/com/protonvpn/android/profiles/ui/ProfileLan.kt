/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.redesign.settings.ui.LanSetting
import com.protonvpn.android.redesign.settings.ui.LocalThemeType
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme

private const val PREFS_NAME = "Storage"
private const val THEME_KEY = "theme"

@Composable
fun ProfileLanRoute(
    viewModel: CreateEditProfileViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // Получаем тему из SharedPreferences для поддержки дизайна
    val themeName = remember(context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(THEME_KEY, ThemeType.System.name)
    }

    val themeType = try {
        ThemeType.valueOf(themeName ?: ThemeType.System.name)
    } catch (e: IllegalArgumentException) {
        ThemeType.System
    }

    // Передаем тему вниз по дереву, чтобы LanSetting мог адаптировать дизайн (карточки, обводки)
    CompositionLocalProvider(LocalThemeType provides themeType) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = ProtonTheme.colors.backgroundNorm)
                .navigationBarsPadding()
        ) {
            val lan = viewModel.lanValuesFlow.collectAsStateWithLifecycle(null).value
            if (lan != null) {
                LanSetting(
                    onClose = onClose,
                    lan = lan,
                    onToggleLan = viewModel::toggleLanConnections,
                    onToggleAllowDirectConnection = viewModel::toggleLanAllowDirectConnections,
                )
            }
        }
    }
}