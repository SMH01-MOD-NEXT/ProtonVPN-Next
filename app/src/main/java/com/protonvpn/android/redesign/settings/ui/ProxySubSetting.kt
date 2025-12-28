/*
 * Copyright (c) 2025 Proton AG
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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import me.proton.core.presentation.R as CoreR

@Composable
fun ProxySubSetting(
    onClose: () -> Unit,
    setting: SettingsViewModel.SettingViewState.Proxy,
    onToggle: () -> Unit,
) {
    val listState = rememberLazyListState()

    FeatureSubSettingScaffold(
        title = stringResource(id = setting.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 0,
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
                setting = setting,
                imageRes = setting.iconRes ?: CoreR.drawable.ic_proton_globe,
                onToggle = onToggle,
                // Исправлено: передаем пустую лямбду вместо null, так как функция не принимает null
                onLearnMore = {},
            )
        }
    }
}