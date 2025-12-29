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

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.ui.settings.formatSplitTunnelingItems
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun SplitTunnelingSubSetting(
    onClose: () -> Unit,
    splitTunneling: SettingsViewModel.SettingViewState.SplitTunneling,
    onLearnMore: () -> Unit,
    onSplitTunnelToggle: () -> Unit,
    onSplitTunnelModeSelected: (SplitTunnelingMode) -> Unit,
    onAppsClick: (SplitTunnelingMode) -> Unit,
    onIpsClick: (SplitTunnelingMode) -> Unit,
    onExportSettings: (android.net.Uri, Context) -> Unit,
    onImportSettings: (android.net.Uri, Context) -> Unit
) {
    var changeModeDialogShown by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Получаем тему из LocalThemeType
    val themeType = LocalThemeType.current

    // Логика стилизации карточки
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    // Launcher for creating a file (Export)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onExportSettings(it, context) }
    }

    // Launcher for opening a file (Import)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportSettings(it, context) }
    }

    // Default export filename from resources
    val exportFilename = stringResource(R.string.settings_split_tunneling_export_filename)

    FeatureSubSettingScaffold(
        title = stringResource(id = splitTunneling.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .largeScreenContentPadding()
            .padding(horizontal = 16.dp)
        val splitTunnelingMode = splitTunneling.mode
        val modeStandard = splitTunnelingMode == SplitTunnelingMode.EXCLUDE_ONLY
        val modeLabel =
            if (modeStandard) R.string.settings_split_tunneling_mode_standard
            else R.string.settings_split_tunneling_mode_inverse
        val appsLabel =
            if (modeStandard) R.string.settings_split_tunneling_excluded_apps
            else R.string.settings_split_tunneling_included_apps
        val ipsLabel =
            if (modeStandard) R.string.settings_split_tunneling_excluded_ips
            else R.string.settings_split_tunneling_included_ips
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                itemModifier = horizontalItemPaddingModifier,
                setting = splitTunneling,
                imageRes = R.drawable.setting_split_tunneling,
                // onToggle убран, переключатель теперь внутри карточки
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
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsFeatureToggle(
                            label = stringResource(splitTunneling.titleRes),
                            checked = splitTunneling.value,
                            onCheckedChange = { _ -> onSplitTunnelToggle() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        if (splitTunneling.value) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = ProtonTheme.colors.separatorNorm
                            )

                            SettingRow(
                                title = stringResource(id = R.string.settings_split_tunneling_mode_title),
                                subtitleComposable = {
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(text = stringResource(modeLabel), style = ProtonTheme.typography.defaultWeak)
                                },
                                onClick = { changeModeDialogShown = true },
                                modifier = Modifier.animateItem()
                            )

                            SettingRowWithIcon(
                                icon = CoreR.drawable.ic_proton_mobile,
                                title = stringResource(id = appsLabel),
                                settingValue =
                                    SettingValue.SettingText(formatSplitTunnelingItems(splitTunneling.currentModeAppNames)),
                                onClick = { onAppsClick(splitTunnelingMode) },
                                modifier = Modifier.animateItem()
                            )

                            SettingRowWithIcon(
                                icon = CoreR.drawable.ic_proton_window_terminal,
                                title = stringResource(id = ipsLabel),
                                settingValue =
                                    SettingValue.SettingText(formatSplitTunnelingItems(splitTunneling.currentModeIps)),
                                onClick = { onIpsClick(splitTunnelingMode) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            if (splitTunneling.value) {
                // --- Import / Export Section ---
                item {
                    Text(
                        text = stringResource(R.string.settings_split_tunneling_backup_restore),
                        style = ProtonTheme.typography.defaultNorm,
                        color = ProtonTheme.colors.textNorm,
                        modifier = horizontalItemPaddingModifier.padding(top = 24.dp, bottom = 8.dp, start = 12.dp)
                    )
                }
                item {
                    Card(
                        modifier = horizontalItemPaddingModifier.padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = border,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            SettingRowWithIcon(
                                icon = CoreR.drawable.ic_proton_arrow_down,
                                title = stringResource(R.string.settings_split_tunneling_import),
                                settingValue = SettingValue.SettingText(stringResource(R.string.settings_split_tunneling_import_desc)),
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.animateItem()
                            )
                            SettingRowWithIcon(
                                icon = CoreR.drawable.ic_proton_arrow_up,
                                title = stringResource(R.string.settings_split_tunneling_export),
                                settingValue = SettingValue.SettingText(stringResource(R.string.settings_split_tunneling_export_desc)),
                                onClick = { exportLauncher.launch(exportFilename) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    if (changeModeDialogShown) {
        ChangeModeDialog(
            isStandardSelected = splitTunneling.mode == SplitTunnelingMode.EXCLUDE_ONLY,
            onStandardSelected = { onSplitTunnelModeSelected(SplitTunnelingMode.EXCLUDE_ONLY) },
            onInverseSelected = { onSplitTunnelModeSelected(SplitTunnelingMode.INCLUDE_ONLY) },
            onDismissRequest = { changeModeDialogShown = false }
        )
    }
}

@Composable
private fun ChangeModeDialog(
    isStandardSelected: Boolean,
    onStandardSelected: () -> Unit,
    onInverseSelected: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ProtonBasicAlert(
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            Text(
                stringResource(R.string.settings_split_tunneling_mode_title),
                style = ProtonTheme.typography.body1Bold,
                modifier = Modifier.padding(horizontal = DIALOG_CONTENT_PADDING)
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsRadioItemSmall(
                title = stringResource(R.string.settings_split_tunneling_mode_standard),
                description = stringResource(R.string.settings_split_tunneling_mode_description_standard),
                selected = isStandardSelected,
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                onSelected = {
                    onStandardSelected()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsRadioItemSmall(
                title = stringResource(R.string.settings_split_tunneling_mode_inverse),
                description = stringResource(R.string.settings_split_tunneling_mode_description_inverse),
                selected = !isStandardSelected,
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                onSelected = {
                    onInverseSelected()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}