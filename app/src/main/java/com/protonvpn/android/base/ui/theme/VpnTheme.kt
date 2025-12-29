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
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.sin
import kotlin.random.Random

private const val PREFS_NAME = "Storage"
private const val THEME_KEY = "theme"

@Composable
fun VpnTheme(isDark: Boolean = isNightMode(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentIsDark = rememberUpdatedState(isDark)

    // State for theme name
    val currentThemeName = remember { mutableStateOf(getThemeName(context)) }

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == THEME_KEY) {
                currentThemeName.value = prefs.getString(THEME_KEY, ThemeType.System.name)
            }
        }
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        currentThemeName.value = getThemeName(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Определяем, нужна ли анимация снега
    val isSnowing = remember(currentThemeName.value) {
        currentThemeName.value == ThemeType.NewYearLight.name ||
                currentThemeName.value == ThemeType.NewYearAmoled.name
    }

    // Вычисляем итоговые цвета
    val protonColors = remember(currentIsDark.value, currentThemeName.value) {
        val selectedTheme = currentThemeName.value
        val baseColors = if (currentIsDark.value) ProtonColors.Dark else ProtonColors.Light

        when (selectedTheme) {
            ThemeType.Amoled.name -> baseColors.toAmoled()
            ThemeType.Gold.name -> baseColors.toGold()
            ThemeType.NewYearLight.name -> baseColors.toNewYearLight()
            ThemeType.NewYearAmoled.name -> baseColors.toNewYearAmoled()
            else -> baseColors
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // Основной контент
                    content()

                    // Эффект падающего снега (накладывается поверх)
                    if (isSnowing) {
                        SnowfallOverlay(isDarkTheme = currentIsDark.value)
                    }
                }
            }
        }
    }
}

// --- Snowfall Effect ---

data class Snowflake(
    var x: Float,
    var y: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val swaySpeed: Float, // Скорость покачивания
    var swayOffset: Float // Текущее смещение для синусоиды
)

@Composable
fun SnowfallOverlay(isDarkTheme: Boolean) {
    // Используем BoxWithConstraints, чтобы получить точные размеры экрана до отрисовки
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Конвертируем dp constraints в пиксели
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Если размеры еще не определены (0), не рисуем
        if (widthPx <= 0 || heightPx <= 0) return@BoxWithConstraints

        // Цвет снежинок: Белый для темной темы, Грифельно-серый для светлой (чтобы было видно)
        val snowColor = if (isDarkTheme) {
            ComposeColor.White
        } else {
            ComposeColor(0xFF708090) // SlateGray, хорошо виден на белом
        }

        // Генерируем снежинки один раз при создании
        val snowflakes = remember {
            mutableStateListOf<Snowflake>().apply {
                repeat(100) { // Количество снежинок
                    add(
                        Snowflake(
                            x = Random.nextFloat() * widthPx,
                            // Генерируем Y случайно по ВСЕЙ высоте, чтобы снег был сразу везде, а не только сверху
                            y = Random.nextFloat() * heightPx,
                            speed = Random.nextFloat() * 2f + 1.5f,
                            radius = Random.nextFloat() * 3f + 2f,
                            alpha = Random.nextFloat() * 0.5f + 0.3f, // Чуть более заметные
                            swaySpeed = Random.nextFloat() * 0.02f + 0.01f,
                            swayOffset = Random.nextFloat() * 100f
                        )
                    )
                }
            }
        }

        // Анимационный цикл
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { _ ->
                    snowflakes.forEach { flake ->
                        // Падение вниз
                        flake.y += flake.speed

                        // Хаотичное покачивание влево-вправо (синусоида)
                        flake.swayOffset += flake.swaySpeed
                        flake.x += (sin(flake.swayOffset) * 0.5f).toFloat()

                        // Если улетела за нижний край
                        if (flake.y > heightPx) {
                            flake.y = -flake.radius // Возвращаем наверх за пределы экрана
                            flake.x = Random.nextFloat() * widthPx // Новая случайная позиция X
                        }

                        // Если улетела за боковые края (из-за покачивания)
                        if (flake.x > widthPx) flake.x = 0f
                        if (flake.x < 0) flake.x = widthPx
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            snowflakes.forEach { flake ->
                drawCircle(
                    color = snowColor,
                    radius = flake.radius,
                    center = Offset(flake.x, flake.y),
                    alpha = flake.alpha
                )
            }
        }
    }
}

// --- Extensions for Color Themes ---

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

/**
 * Gold: Dark base + Rich Gold Accents + Golden Text
 */
private fun ProtonColors.toGold(): ProtonColors {
    // Более насыщенные и теплые золотые оттенки
    val richGold = ComposeColor(0xFFFFD700)      // Классическое золото
    val deepGold = ComposeColor(0xFFFFC107)      // Янтарное золото (Amber)
    val paleGold = ComposeColor(0xFFFFE082)      // Светлое золото (для текста)

    return this.copy(
        // Основной текст - делаем более насыщенным
        textNorm = paleGold,
        textWeak = ComposeColor(0xFFFFCA28), // Чуть темнее
        textAccent = richGold,
        textHint = ComposeColor(0xFFBCAAA4), // Бронзовый оттенок для хинтов

        // Иконки
        iconNorm = paleGold,
        iconWeak = ComposeColor(0xFFFFCA28),
        iconAccent = richGold,

        // Брендовые цвета (Кнопки)
        brandNorm = richGold,
        brandDarken20 = deepGold,
        brandLighten20 = ComposeColor(0xFFFFECB3),

        // Интерактивные элементы
        interactionNorm = richGold,
        interactionPressed = deepGold,

        // Разделители
        separatorNorm = ComposeColor(0x4DFFD700), // Полупрозрачное золото

        // Уведомления
        notificationNorm = richGold
    )
}

private fun ProtonColors.toNewYearLight(): ProtonColors {
    val santaRed = ComposeColor(0xFFD32F2F) // Насыщенный красный
    val pineGreen = ComposeColor(0xFF2E7D32) // Насыщенный зеленый

    return this.copy(
        brandNorm = santaRed,
        brandDarken20 = ComposeColor(0xFFB71C1C),
        brandLighten20 = ComposeColor(0xFFEF5350),

        textAccent = santaRed,
        iconAccent = pineGreen,

        interactionNorm = santaRed,
        interactionPressed = ComposeColor(0xFFC62828),

        notificationSuccess = pineGreen,
    )
}

private fun ProtonColors.toNewYearAmoled(): ProtonColors {
    val amoledBase = this.toAmoled()
    val neonRed = ComposeColor(0xFFFF1744) // Яркий неон красный
    val neonGreen = ComposeColor(0xFF00E676) // Яркий неон зеленый

    return amoledBase.copy(
        brandNorm = neonRed,
        brandDarken20 = ComposeColor(0xFFD50000),

        textAccent = neonRed,
        iconAccent = neonGreen,

        interactionNorm = neonRed,
        notificationSuccess = neonGreen
    )
}

// --- Utils ---

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