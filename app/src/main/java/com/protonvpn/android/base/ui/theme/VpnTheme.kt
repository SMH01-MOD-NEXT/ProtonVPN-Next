/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.base.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import com.protonvpn.android.base.ui.LocalLocale
import com.protonvpn.android.base.ui.LocalStringProvider
import com.protonvpn.android.base.ui.StringProvider
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonColors
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.ProtonTheme3
import me.proton.core.compose.theme.defaultUnspecified
import me.proton.core.compose.theme.isNightMode
import me.proton.core.presentation.utils.currentLocale
import androidx.compose.ui.graphics.Color as ComposeColor

private const val TAG = "VpnThemeLog"
private const val PREFS_NAME = "Storage"
private const val THEME_KEY = "theme"

@Composable
fun VpnTheme(isDark: Boolean = isNightMode(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentIsDark = rememberUpdatedState(isDark)

    // Храним текущую тему в стейте, инициализируем чтением с диска
    val currentThemeName = remember { mutableStateOf(getThemeName(context)) }

    // Создаем слушателя через remember, чтобы GC не удалил его (SharedPreferences хранит WeakReference)
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == THEME_KEY) {
                val newValue = prefs.getString(THEME_KEY, ThemeType.System.name)
                Log.d(TAG, "Listener triggered. Key: $key, New Value: $newValue")
                currentThemeName.value = newValue
            }
        }
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // --- ДИАГНОСТИКА: Выводим все ключи, чтобы найти правильный ---
        val allEntries = prefs.all
        Log.d(TAG, "All keys in '$PREFS_NAME': ${allEntries.keys.joinToString()}")
        Log.d(TAG, "Current value for '$THEME_KEY': ${allEntries[THEME_KEY]}")
        // -------------------------------------------------------------

        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Обновляем значение при входе, на случай если оно изменилось до подписки
        currentThemeName.value = getThemeName(context)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Логика определения Amoled
    val isAmoled = remember(currentIsDark.value, currentThemeName.value) {
        val theme = currentThemeName.value
        val result = currentIsDark.value && (theme == ThemeType.Amoled.name)
        Log.d(TAG, "Re-calc isAmoled. Dark=${currentIsDark.value}, Theme=$theme -> Result=$result")
        result
    }

    val baseColors = if (currentIsDark.value) ProtonColors.Dark else ProtonColors.Light

    val protonColors = remember(baseColors, isAmoled) {
        if (isAmoled) {
            Log.i(TAG, ">>> Applying AMOLED (Black) colors <<<")
            baseColors.toAmoled()
        } else {
            Log.d(TAG, "Applying Standard colors")
            baseColors
        }
    }

    ProtonTheme(colors = protonColors) {
        ProtonTheme3(colors = protonColors) {
            CompositionLocalProvider(
                LocalContentColor provides ProtonTheme.colors.textNorm,
                LocalTextStyle provides ProtonTheme.typography.defaultUnspecified,
                LocalStringProvider provides StringProvider { id, formatArgs -> stringResource(id, *formatArgs) },
                LocalLocale provides LocalConfiguration.current.currentLocale(),
            ) {
                content()
            }
        }
    }
}

/**
 * Создает копию цветов, заменяя фоновые оттенки на чистый черный (#000000).
 */
private fun ProtonColors.toAmoled(): ProtonColors {
    val black = ComposeColor.Black
    return this.copy(
        backgroundNorm = black,
        backgroundSecondary = black,
        backgroundDeep = black,
        shade0 = black,
        shade10 = black,
        shade15 = black,
        sidebarColors = this.sidebarColors?.copy(
            backgroundNorm = black,
            backgroundSecondary = black,
            backgroundDeep = black,
            shade0 = black,
            shade10 = black,
            shade15 = black
        )
    )
}

val LineHeightStyle.Companion.NoTrim: LineHeightStyle
    get() = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None)

fun ComponentActivity.enableEdgeToEdgeVpn() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    if (Build.VERSION.SDK_INT >= 29) {
        window.isNavigationBarContrastEnforced = false
    }
}

private fun getThemeName(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(THEME_KEY, ThemeType.System.name)
}