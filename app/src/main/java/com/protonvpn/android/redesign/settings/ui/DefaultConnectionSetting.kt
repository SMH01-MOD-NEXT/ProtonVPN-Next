/*
 * Copyright (c) 2024. Proton AG
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonRadio
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.FlagDimensions
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.recents.ui.DefaultConnectionViewModel
import com.protonvpn.android.redesign.recents.usecases.DefaultConnItem
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentBlankRow
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun DefaultConnectionSetting(onClose: () -> Unit) {
    val themeType = LocalThemeType.current
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null
    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    BasicSubSetting(
        title = stringResource(id = R.string.settings_default_connection_title),
        onClose = onClose
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .largeScreenContentPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = border,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DefaultConnectionSelection(
                        onClose = onClose,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultConnectionSelection(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val viewModel: DefaultConnectionViewModel = hiltViewModel()
    val defaultConnectionViewState =
        viewModel.defaultConnectionViewState.collectAsStateWithLifecycle(null)
    val onSelected = { defaultConnection: DefaultConnItem ->
        viewModel.setNewDefaultConnection(defaultConnection)
        onClose()
    }
    defaultConnectionViewState.value?.let { state ->
        Column(
            modifier = modifier
        ) {
            state.recents.forEach { item ->
                when (item) {
                    is DefaultConnItem.DefaultConnItemViewState -> {
                        DefaultSelectionRow(
                            leadingIcon = { ConnectIntentIcon(item.connectIntentViewState.primaryLabel) },
                            title = item.connectIntentViewState.primaryLabel.label(),
                            subTitle = item.connectIntentViewState.secondaryLabel?.label(),
                            serverFeatures = item.connectIntent.features,
                            isSelected = item.isDefaultConnection,
                            onSelected = { onSelected(item) }
                        )
                    }

                    is DefaultConnItem.PreDefinedItem -> {
                        DefaultSelectionRow(
                            leadingIcon = {
                                if (item is DefaultConnItem.MostRecentItem)
                                    IconRecent()
                                else
                                    Flag(CountryId.fastest)
                            },
                            title = stringResource(id = item.titleRes),
                            subTitle = buildAnnotatedString {
                                append(stringResource(id = item.subtitleRes))
                            },
                            isSelected = item.isDefaultConnection,
                            serverFeatures = emptySet(),
                            onSelected = { onSelected(item) }
                        )
                    }

                    is DefaultConnItem.HeaderSeparator -> {
                        Text(
                            text = stringResource(item.titleRes),
                            style = ProtonTheme.typography.body2Regular,
                            color = ProtonTheme.colors.textWeak,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSelectionRow(
    leadingIcon: @Composable () -> Unit,
    title: String,
    subTitle: AnnotatedString?,
    serverFeatures: Set<ServerFeature>,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    ConnectIntentBlankRow(
        leadingComposable = leadingIcon,
        trailingComposable = {
            ProtonRadio(
                selected = isSelected,
                onClick = onSelected,
                modifier = Modifier.clearAndSetSemantics { }
            )
        },
        title = title,
        subTitle = subTitle,
        serverFeatures = serverFeatures,
        isUnavailable = false,
        modifier = Modifier
            .selectable(isSelected, onClick = onSelected)
            .padding(horizontal = 16.dp)
    )
}

@Composable
fun IconRecent(
    modifier: Modifier = Modifier,
) {
    Image(
        painterResource(id = me.proton.core.presentation.R.drawable.ic_proton_clock_rotate_left),
        colorFilter = ColorFilter.tint(ProtonTheme.colors.iconNorm),
        contentDescription = null,
        modifier = modifier
            .background(color = ProtonTheme.colors.shade40, shape = FlagDimensions.regularShape)
            .size(FlagDimensions.singleFlagSize)
            .padding(2.dp)
            .clip(FlagDimensions.regularShape)
    )
}

@Preview
@Composable
fun DefaultSelectionPreview() {
    ProtonVpnPreview {
        Column {
            DefaultSelectionRow(
                leadingIcon = { IconRecent() },
                title = "Most recent",
                subTitle = buildAnnotatedString { append("#53-TOR") },
                serverFeatures = setOf(ServerFeature.Tor),
                isSelected = true,
                onSelected = {}
            )
        }
    }
}