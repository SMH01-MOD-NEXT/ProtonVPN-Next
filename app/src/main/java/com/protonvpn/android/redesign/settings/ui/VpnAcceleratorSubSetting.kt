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

package com.protonvpn.android.redesign.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun VpnAcceleratorSubSetting(
    onClose: () -> Unit,
    vpnAccelerator: SettingsViewModel.SettingViewState.VpnAccelerator,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit
) {
    val listState = rememberLazyListState()

    // Получаем тему из LocalThemeType (предполагается, что он доступен в этом контексте, как в примере)
    val themeType = LocalThemeType.current

    // Логика стилизации карточки
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    FeatureSubSettingScaffold(
        title = stringResource(id = vpnAccelerator.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .largeScreenContentPadding()
            .padding(horizontal = 16.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                itemModifier = horizontalItemPaddingModifier,
                setting = vpnAccelerator,
                imageRes = R.drawable.setting_vpn_accelerator, // Используем ресурс изображения для VPN Accelerator
                onLearnMore = onLearnMore,
            )

            // Main Settings Card
            item {
                Card(
                    modifier = horizontalItemPaddingModifier.padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    SettingsFeatureToggle(
                        label = stringResource(vpnAccelerator.titleRes),
                        checked = vpnAccelerator.value,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}