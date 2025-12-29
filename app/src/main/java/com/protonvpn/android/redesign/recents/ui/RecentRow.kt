/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private const val PREFS_NAME = "Storage"
private const val THEME_KEY = "theme"

data class RecentItemViewState(
    val id: Long,
    val connectIntent: ConnectIntentViewState,
    val isPinned: Boolean,
    val isConnected: Boolean,
    val availability: ConnectIntentAvailability,
)

@Composable
fun RecentRow(
    item: RecentItemViewState,
    onClick: () -> Unit,
    onRecentSettingOpen: (RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Получаем текущую тему напрямую из SharedPreferences, так как LocalThemeType может быть недоступен
    val themeName = remember(context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(THEME_KEY, ThemeType.System.name)
    }

    val isAmoled = themeName == ThemeType.Amoled.name || themeName == ThemeType.NewYearAmoled.name

    // Белая обводка 1dp только для AMOLED
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    // Определение светлой темы для цвета карточки
    val isLight = themeName == ThemeType.Light.name || themeName == ThemeType.NewYearLight.name ||
            (themeName == ThemeType.System.name && !isSystemInDarkTheme())

    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    val pinnedStateDescription = stringResource(id = R.string.recent_action_accessibility_state_pinned)
    val iconRes =
        if (item.isPinned) CoreR.drawable.ic_proton_pin_filled else CoreR.drawable.ic_proton_clock_rotate_left

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ConnectIntentRow(
            availability = item.availability,
            connectIntent = item.connectIntent,
            isConnected = item.isConnected,
            onClick = onClick,
            onOpen = { onRecentSettingOpen(item) },
            leadingComposable = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        tint = ProtonTheme.colors.iconWeak,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    ConnectIntentIcon(item.connectIntent.primaryLabel)
                }
            },
            modifier = Modifier,
            semanticsStateDescription = if (item.isPinned) pinnedStateDescription else null
        )
    }
}

@Preview
@Composable
private fun PreviewRecent() {
    VpnTheme {
        RecentRow(
            item = RecentItemViewState(
                id = 0,
                ConnectIntentViewState(
                    primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.switzerland, CountryId.sweden),
                    secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(null, CountryId.sweden),
                    serverFeatures = emptySet()
                ),
                isPinned = false,
                isConnected = true,
                availability = ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ),
            onClick = {},
            onRecentSettingOpen = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}