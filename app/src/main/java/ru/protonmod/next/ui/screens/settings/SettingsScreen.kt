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

package ru.protonmod.next.ui.screens.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.BorderStroke
import ru.protonmod.next.ui.utils.isTablet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import dagger.hilt.android.EntryPointAccessors
import ru.protonmod.next.di.AppEntryPoint
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.MainHeader
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet
import androidx.compose.ui.platform.LocalLocale
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.ui.widget.VpnWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    onNavigateToThemeSelection: (() -> Unit)? = null,
    onNavigateToLoadDisplayMode: (() -> Unit)? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToBackup: (() -> Unit)? = null,
    onNavigateToCustomDns: (() -> Unit)? = null,
    onNavigateToCountrySpoofing: (() -> Unit)? = null,
    onNavigateToPortSelection: ((Int) -> Unit)? = null,
    onNavigateToCertSettings: (() -> Unit)? = null,
    onNavigateToNetShield: (() -> Unit)? = null,
    onNavigateToConnectionVerification: (() -> Unit)? = null,
    onNavigateToAiSettings: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration (immersive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.backgroundNorm.copy(alpha = 0.1f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            SettingsContent(
                state = uiState,
                isTablet = isTablet,
                onAutoConnectChange = viewModel::setAutoConnect,
                onReconnectHintChange = viewModel::setReconnectHintEnabled,
                onNotificationsChange = viewModel::setNotifications,
                onLogout = viewModel::logout,
                onAllowLanChange = viewModel::setAllowLanEnabled,
                onTorModeChange = viewModel::setTorModeEnabled,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onNavigateToProtocol = onNavigateToProtocol,
                onNavigateToKillSwitch = onNavigateToKillSwitch,
                onNavigateToApiBypass = onNavigateToApiBypass,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToErrorReporting = onNavigateToErrorReporting,
                onNavigateToThemeSelection = onNavigateToThemeSelection,
                onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode,
                onNavigateToDebug = onNavigateToDebug,
                onNavigateToBackup = onNavigateToBackup,
                onNavigateToCustomDns = onNavigateToCustomDns,
                onNavigateToCountrySpoofing = onNavigateToCountrySpoofing,
                onNavigateToPortSelection = onNavigateToPortSelection,
                onNavigateToCertSettings = onNavigateToCertSettings,
                onNavigateToNetShield = onNavigateToNetShield,
                onNavigateToConnectionVerification = onNavigateToConnectionVerification,
                onNavigateToAiSettings = onNavigateToAiSettings,
                onOtaFrequencyChange = viewModel::setOtaUpdateFrequency,
                onCheckForUpdates = viewModel::checkForUpdates,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        }
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onReconnectHintChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAllowLanChange: (Boolean) -> Unit,
    onTorModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOtaFrequencyChange: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    onNavigateToThemeSelection: (() -> Unit)? = null,
    onNavigateToLoadDisplayMode: (() -> Unit)? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToBackup: (() -> Unit)? = null,
    onNavigateToCustomDns: (() -> Unit)? = null,
    onNavigateToCountrySpoofing: (() -> Unit)? = null,
    onNavigateToPortSelection: ((Int) -> Unit)? = null,
    onNavigateToCertSettings: (() -> Unit)? = null,
    onNavigateToNetShield: (() -> Unit)? = null,
    onNavigateToConnectionVerification: (() -> Unit)? = null,
    onNavigateToAiSettings: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = if (isTablet) 140.dp else 120.dp
        )
    ) {
        item(contentType = "Header") {
            MainHeader(title = stringResource(R.string.settings_title))
            
            val context = LocalContext.current
            val nextVpnManager = remember { EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java).nextVpnManager() }
            var isOfficialBuild by remember { mutableStateOf(true) }
            
            LaunchedEffect(nextVpnManager) {
                isOfficialBuild = !nextVpnManager.isTamperDetected()
            }
            
            if (!isOfficialBuild) {
                TamperSettingsBanner(onShowDownloads = { 
                    // No longer supported via Kotlin call
                })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (isTablet) {
            item(contentType = "TabletContent") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1000.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Left Column: Main Settings & Connection
                    Column(modifier = Modifier.weight(1f)) {
                        FeatureCategory(
                            isTablet = true,
                            state = state,
                            onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                            onNavigateToProtocol = onNavigateToProtocol
                        )

                        ConnectionSettingsSection(
                            state = state,
                            onAutoConnectChange = onAutoConnectChange,
                            onReconnectHintChange = onReconnectHintChange,
                            onNavigateToApiBypass = onNavigateToApiBypass,
                            onNavigateToPortSelection = onNavigateToPortSelection,
                            onNavigateToCertSettings = onNavigateToCertSettings
                        )

                        CustomizationSettingsSection(
                            state = state,
                            onNavigateToThemeSelection = onNavigateToThemeSelection,
                            onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode
                        )
                    }

                    // Right Column: Privacy, Notifications & About
                    Column(modifier = Modifier.weight(1f)) {
                        PrivacySettingsSection(
                            state = state,
                            onNavigateToCustomDns = onNavigateToCustomDns,
                            onNavigateToCountrySpoofing = onNavigateToCountrySpoofing,
                            onNavigateToKillSwitch = onNavigateToKillSwitch,
                            onNavigateToErrorReporting = onNavigateToErrorReporting,
                            onNavigateToNetShield = onNavigateToNetShield,
                            onNavigateToConnectionVerification = onNavigateToConnectionVerification,
                            onAllowLanChange = onAllowLanChange,
                            onTorModeChange = onTorModeChange,
                            onNotificationsChange = onNotificationsChange
                        )

                        AiSettingsSection(
                            state = state,
                            onNavigateToAiSettings = onNavigateToAiSettings
                        )

                        if (!state.isPrivacyBuild) {
                            UpdateSettingsSection(
                                state = state,
                                onFrequencyChange = onOtaFrequencyChange,
                                onCheckNow = onCheckForUpdates
                            )
                        }

                        WidgetSettingsSection()

                        AboutSettingsSection(
                            onNavigateToAbout = onNavigateToAbout,
                            onNavigateToDebug = onNavigateToDebug,
                            onNavigateToBackup = onNavigateToBackup,
                            onLogout = onLogout
                        )
                    }
                }
            }
        } else {
            // Phone Layout
            val contentModifier = Modifier.fillMaxWidth()

            item(contentType = "FeatureCategory") {
                FeatureCategory(
                    state = state,
                    modifier = contentModifier,
                    isTablet = false,
                    onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                    onNavigateToProtocol = onNavigateToProtocol
                )
            }

            item(contentType = "ConnectionSettings") {
                ConnectionSettingsSection(
                    state = state,
                    onAutoConnectChange = onAutoConnectChange,
                    onReconnectHintChange = onReconnectHintChange,
                    modifier = contentModifier,
                    onNavigateToApiBypass = onNavigateToApiBypass,
                    onNavigateToPortSelection = onNavigateToPortSelection,
                    onNavigateToCertSettings = onNavigateToCertSettings
                )
            }

            item(contentType = "CustomizationSettings") {
                CustomizationSettingsSection(
                    state = state,
                    modifier = contentModifier,
                    onNavigateToThemeSelection = onNavigateToThemeSelection,
                    onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode
                )
            }

            item(contentType = "PrivacySettings") {
                PrivacySettingsSection(
                    state = state,
                    onNotificationsChange = onNotificationsChange,
                    modifier = contentModifier,
                    onNavigateToCustomDns = onNavigateToCustomDns,
                    onNavigateToCountrySpoofing = onNavigateToCountrySpoofing,
                    onNavigateToKillSwitch = onNavigateToKillSwitch,
                    onNavigateToErrorReporting = onNavigateToErrorReporting,
                    onNavigateToNetShield = onNavigateToNetShield,
                    onNavigateToConnectionVerification = onNavigateToConnectionVerification,
                    onAllowLanChange = onAllowLanChange,
                    onTorModeChange = onTorModeChange
                )
            }

            item(contentType = "AiSettings") {
                AiSettingsSection(
                    state = state,
                    modifier = contentModifier,
                    onNavigateToAiSettings = onNavigateToAiSettings
                )
            }

            if (!state.isPrivacyBuild) {
                item(contentType = "UpdateSettings") {
                    UpdateSettingsSection(
                    state = state,
                    onFrequencyChange = onOtaFrequencyChange,
                    onCheckNow = onCheckForUpdates,
                    modifier = contentModifier
                )
                }
            }

            item(contentType = "WidgetSettings") {
                WidgetSettingsSection(modifier = contentModifier)
            }

            item(contentType = "AboutSettings") {
                AboutSettingsSection(
                    onLogout = onLogout,
                    modifier = contentModifier,
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToDebug = onNavigateToDebug,
                    onNavigateToBackup = onNavigateToBackup
                )
            }
        }
    }
}

@Composable
private fun UpdateSettingsSection(
    state: SettingsUiState,
    onFrequencyChange: (String) -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFrequencyDialog by remember { mutableStateOf(false) }

    SettingsCategory(modifier = modifier, title = stringResource(R.string.ota_title)) {
        val currentFrequencyName = when (state.otaUpdateFrequency) {
            "hourly" -> stringResource(R.string.ota_freq_hourly)
            "daily" -> stringResource(R.string.ota_freq_daily)
            "weekly" -> stringResource(R.string.ota_freq_weekly)
            "monthly" -> stringResource(R.string.ota_freq_monthly)
            "disabled" -> stringResource(R.string.ota_freq_disabled)
            else -> state.otaUpdateFrequency
        }

        SettingRowWithIcon(
            icon = Icons.Rounded.SystemUpdate,
            title = stringResource(R.string.ota_check_frequency),
            subtitle = currentFrequencyName,
            onClick = { showFrequencyDialog = true }
        )

        val updateStatus = when {
            state.isCheckingForUpdates -> stringResource(R.string.ota_status_checking)
            state.isUpdateAvailable -> stringResource(R.string.ota_new_version, "") // Version code is not easily available here, but the text will indicate update
            else -> stringResource(R.string.ota_status_up_to_date)
        }

        SettingRowWithIcon(
            icon = Icons.Rounded.Refresh,
            title = stringResource(R.string.ota_btn_check),
            subtitle = updateStatus,
            onClick = onCheckNow,
            enabled = !state.isCheckingForUpdates
        )
    }

    if (showFrequencyDialog) {
        val options = listOf("hourly", "daily", "weekly", "monthly", "disabled")
        val optionNames = listOf(
            stringResource(R.string.ota_freq_hourly),
            stringResource(R.string.ota_freq_daily),
            stringResource(R.string.ota_freq_weekly),
            stringResource(R.string.ota_freq_monthly),
            stringResource(R.string.ota_freq_disabled)
        )

        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text(stringResource(R.string.ota_check_frequency)) },
            text = {
                Column {
                    options.forEachIndexed { index, option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFrequencyChange(option)
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.otaUpdateFrequency == option,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = ProtonNextTheme.colors.brandNorm)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(optionNames[index], color = ProtonNextTheme.colors.textNorm)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = ProtonNextTheme.colors.backgroundSecondary
        )
    }
}

@Composable
private fun WidgetSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val isSupported = remember { appWidgetManager.isRequestPinAppWidgetSupported }

    if (isSupported) {
        SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_widget)) {
            SettingRowWithIcon(
                icon = Icons.Rounded.Widgets,
                title = stringResource(R.string.settings_widget_add_to_home),
                subtitle = stringResource(R.string.settings_widget_add_to_home_desc),
                onClick = {
                    val myProvider = ComponentName(context, VpnWidgetProvider::class.java)
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                }
            )
        }
    }
}

@Composable
private fun ConnectionSettingsSection(
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onReconnectHintChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToPortSelection: ((Int) -> Unit)? = null,
    onNavigateToCertSettings: (() -> Unit)? = null
) {
    SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_connection)) {
        SettingToggleRow(
            icon = Icons.Rounded.Autorenew,
            title = stringResource(R.string.settings_auto_connect),
            subtitle = stringResource(R.string.settings_auto_connect_desc),
            checked = state.autoConnectEnabled,
            onCheckedChange = onAutoConnectChange
        )

        SettingToggleRow(
            icon = Icons.Rounded.NotificationImportant,
            title = stringResource(R.string.settings_reconnect_hint),
            subtitle = stringResource(R.string.settings_reconnect_hint_desc),
            checked = state.reconnectHintEnabled,
            onCheckedChange = onReconnectHintChange
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.settings_api_bypass),
            subtitle = if (state.apiBypassEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            onClick = { onNavigateToApiBypass?.invoke() }
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.Numbers,
            title = stringResource(R.string.settings_port),
            subtitle = if (state.vpnPort == 0) stringResource(R.string.settings_port_auto) else state.vpnPort.toString(),
            onClick = { onNavigateToPortSelection?.invoke(state.vpnPort) }
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.settings_cert_management),
            subtitle = stringResource(R.string.settings_cert_management_desc),
            onClick = { onNavigateToCertSettings?.invoke() }
        )
    }
}

@Composable
private fun CustomizationSettingsSection(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onNavigateToThemeSelection: (() -> Unit)? = null,
    onNavigateToLoadDisplayMode: (() -> Unit)? = null
) {
    SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_customization)) {
        val currentThemeName = when (state.appTheme) {
            AppTheme.SYSTEM -> stringResource(R.string.theme_system)
            AppTheme.LIGHT -> stringResource(R.string.theme_light)
            AppTheme.DARK -> stringResource(R.string.theme_dark)
            AppTheme.AMOLED -> stringResource(R.string.theme_amoled)
            AppTheme.GOLD_LIGHT -> stringResource(R.string.theme_gold_light)
            AppTheme.GOLD_DARK -> stringResource(R.string.theme_gold_dark)
            AppTheme.GOLD_AMOLED -> stringResource(R.string.theme_gold_amoled)
            AppTheme.SURFSHARK -> stringResource(R.string.theme_surfshark)
            AppTheme.NORD -> stringResource(R.string.theme_nord)
            AppTheme.IPVANISH -> stringResource(R.string.theme_ipvanish)
            AppTheme.PUREVPN -> stringResource(R.string.theme_purevpn)
            AppTheme.MULLVAD -> stringResource(R.string.theme_mullvad)
            AppTheme.WINDSCRIBE -> stringResource(R.string.theme_windscribe)
            AppTheme.NOTHING -> stringResource(R.string.theme_nothing)
        }

        SettingRowWithIcon(
            title = stringResource(R.string.settings_app_theme),
            subtitle = currentThemeName,
            icon = Icons.Rounded.Palette,
            onClick = { onNavigateToThemeSelection?.invoke() }
        )

        val currentLoadModeName = when (state.serverLoadDisplayMode) {
            ServerLoadDisplayMode.ALL -> stringResource(R.string.load_mode_all)
            ServerLoadDisplayMode.LINE -> stringResource(R.string.load_mode_line)
            ServerLoadDisplayMode.PERCENT -> stringResource(R.string.load_mode_percent)
            ServerLoadDisplayMode.HIDDEN -> stringResource(R.string.load_mode_hidden)
        }

        SettingRowWithIcon(
            title = stringResource(R.string.settings_load_display_mode),
            subtitle = currentLoadModeName,
            icon = Icons.Rounded.BarChart,
            onClick = { onNavigateToLoadDisplayMode?.invoke() }
        )
    }
}

@Composable
private fun PrivacySettingsSection(
    state: SettingsUiState,
    onNotificationsChange: (Boolean) -> Unit,
    onAllowLanChange: (Boolean) -> Unit,
    onTorModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToCustomDns: (() -> Unit)? = null,
    onNavigateToCountrySpoofing: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    onNavigateToNetShield: (() -> Unit)? = null,
    onNavigateToConnectionVerification: (() -> Unit)? = null
) {
    SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_privacy)) {
        val currentDnsSubtitle = state.customDns.ifBlank {
            stringResource(R.string.settings_custom_dns_default)
        }

        SettingRowWithIcon(
            icon = ImageVector.vectorResource(R.drawable.ic_proton_netshield),
            title = stringResource(R.string.netshield_title),
            subtitle = stringResource(R.string.netshield_settings_subtitle),
            onClick = onNavigateToNetShield
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.HealthAndSafety,
            title = stringResource(R.string.verification_title),
            subtitle = stringResource(R.string.verification_settings_subtitle),
            onClick = onNavigateToConnectionVerification
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.settings_custom_dns),
            subtitle = currentDnsSubtitle,
            onClick = onNavigateToCustomDns
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.Public,
            title = stringResource(R.string.settings_country_spoofing_title),
            subtitle = if (state.spoofCountryEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            onClick = onNavigateToCountrySpoofing
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.GppMaybe,
            title = stringResource(R.string.settings_kill_switch),
            subtitle = stringResource(R.string.settings_kill_switch_desc),
            onClick = onNavigateToKillSwitch
        )

        SettingToggleRow(
            icon = Icons.Rounded.Hub,
            title = stringResource(R.string.settings_tor_mode),
            subtitle = stringResource(R.string.settings_tor_mode_desc),
            checked = state.torModeEnabled,
            onCheckedChange = onTorModeChange
        )

        if (BuildConfig.SENTRY_ENABLED) {
            SettingRowWithIcon(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_error_reporting),
                subtitle = stringResource(R.string.settings_error_reporting_desc),
                onClick = onNavigateToErrorReporting
            )
        }

        SettingToggleRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_notifications),
            subtitle = stringResource(R.string.settings_notifications_desc),
            checked = state.notificationsEnabled,
            onCheckedChange = onNotificationsChange
        )

        SettingToggleRow(
            icon = Icons.Rounded.Lan,
            title = stringResource(R.string.settings_allow_lan),
            subtitle = stringResource(R.string.settings_allow_lan_desc),
            checked = state.allowLanEnabled,
            onCheckedChange = onAllowLanChange
        )
    }
}

@Composable
private fun AiSettingsSection(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onNavigateToAiSettings: (() -> Unit)? = null
) {
    SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_ai)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.ai_settings_title),
            subtitle = if (state.aiEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            onClick = { onNavigateToAiSettings?.invoke() }
        )
    }
}

@Composable
private fun AboutSettingsSection(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToBackup: (() -> Unit)? = null
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    SettingsCategory(modifier = modifier, title = stringResource(R.string.settings_about)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            onClick = onNavigateToAbout
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.Backup,
            title = stringResource(R.string.backup_title),
            subtitle = stringResource(R.string.backup_export_desc),
            onClick = onNavigateToBackup
        )

        if (BuildConfig.DEBUG) {
            SettingRowWithIcon(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_debug),
                subtitle = stringResource(R.string.debug_title),
                onClick = onNavigateToDebug
            )
        }

        SettingRowWithIcon(
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = stringResource(R.string.btn_logout),
            subtitle = stringResource(R.string.desc_toggle_connection),
            onClick = { showLogoutDialog = true },
            titleColor = ProtonNextTheme.colors.notificationError
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.btn_logout)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ProtonNextTheme.colors.notificationError)
                ) {
                    Text(stringResource(R.string.btn_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = ProtonNextTheme.colors.backgroundSecondary,
            titleContentColor = ProtonNextTheme.colors.textNorm,
            textContentColor = ProtonNextTheme.colors.textWeak
        )
    }
}

@Composable
private fun FeatureCategory(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = if (isTablet) Arrangement.Start else Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tileModifier = if (isTablet) Modifier.size(160.dp) else Modifier.weight(1f)

        // Split Tunneling Tile
        FeatureTile(
            modifier = tileModifier,
            title = stringResource(id = R.string.settings_split_tunneling),
            subtitle = if (state.splitTunnelingEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            icon = Icons.AutoMirrored.Rounded.AltRoute,
            isActive = state.splitTunnelingEnabled,
            onClick = { onNavigateToSplitTunnelingMain?.invoke() }
        )

        if (isTablet) Spacer(modifier = Modifier.width(16.dp))

        // Protocol Tile
        FeatureTile(
            modifier = tileModifier,
            title = stringResource(id = R.string.settings_protocol),
            subtitle = "AmneziaWG",
            icon = Icons.Rounded.Security,
            isActive = true,
            onClick = { onNavigateToProtocol?.invoke() }
        )
    }
}

@Composable
fun TamperSettingsBanner(
    onShowDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale.language
    val nextVpnManager = remember { EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java).nextVpnManager() }
    val title = remember { nextVpnManager.getProtectedString(locale, "tamper_warning_title") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onShowDownloads() },
        shape = RoundedCornerShape(12.dp),
        color = colors.notificationError.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, colors.notificationError.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.notificationError,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.notificationError,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.notificationError,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun FeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                alpha = if (isActive) 0.3f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colors.brandNorm.copy(alpha = 0.15f)
                            else colors.backgroundSecondary.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) colors.brandNorm else colors.iconWeak,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textNorm
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
