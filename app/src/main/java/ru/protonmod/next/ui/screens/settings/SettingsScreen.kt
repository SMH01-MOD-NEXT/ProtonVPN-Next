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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToCountries: (() -> Unit)? = null,
    onNavigateToProfiles: (() -> Unit)? = null,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val currentTarget = MainTarget.Settings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.2f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            SettingsContent(
                state = uiState,
                onKillSwitchChange = viewModel::setKillSwitch,
                onAutoConnectChange = viewModel::setAutoConnect,
                onNotificationsChange = viewModel::setNotifications,
                onPortChange = viewModel::setVpnPort,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onNavigateToProtocol = onNavigateToProtocol,
                onNavigateToAbout = onNavigateToAbout,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            )

            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                showCountries = true,
                showGateways = false,
                navigateTo = { target ->
                    when (target) {
                        MainTarget.Home -> onNavigateToHome?.invoke()
                        MainTarget.Countries -> onNavigateToCountries?.invoke()
                        MainTarget.Profiles -> onNavigateToProfiles?.invoke()
                        MainTarget.Settings -> { /* Already here */ }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onKillSwitchChange: (Boolean) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 120.dp
        )
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
            )
        }

        // Feature Tiles: Split Tunneling & Protocol
        item {
            FeatureCategory(
                state = state,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onNavigateToProtocol = onNavigateToProtocol
            )
        }

        // Connection Settings
        item {
            Category(title = stringResource(R.string.settings_connection)) {
                SettingToggleRow(
                    icon = Icons.Rounded.Autorenew,
                    title = stringResource(R.string.settings_auto_connect),
                    subtitle = stringResource(R.string.settings_auto_connect_desc),
                    checked = state.autoConnectEnabled,
                    onCheckedChange = onAutoConnectChange
                )
                
                var showPortDialog by remember { mutableStateOf(false) }
                SettingRowWithIcon(
                    icon = Icons.Rounded.Numbers,
                    title = stringResource(R.string.settings_port),
                    subtitle = if (state.vpnPort == 0) stringResource(R.string.settings_port_auto) else state.vpnPort.toString(),
                    onClick = { showPortDialog = true }
                )
                if (showPortDialog) {
                    PortSelectionDialog(
                        currentPort = state.vpnPort,
                        onDismiss = { showPortDialog = false },
                        onPortSelected = {
                            onPortChange(it)
                            showPortDialog = false
                        }
                    )
                }
            }
        }

        // Privacy & Notifications
        item {
            Category(title = stringResource(R.string.settings_privacy)) {
                SettingToggleRow(
                    icon = Icons.Rounded.GppMaybe,
                    title = stringResource(R.string.settings_kill_switch),
                    subtitle = stringResource(R.string.settings_kill_switch_desc),
                    checked = state.killSwitchEnabled,
                    onCheckedChange = onKillSwitchChange
                )
                SettingToggleRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = state.notificationsEnabled,
                    onCheckedChange = onNotificationsChange
                )
            }
        }

        // About
        item {
            Category(title = stringResource(R.string.settings_about)) {
                SettingRowWithIcon(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_version, "12.0.0"),
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}

@Composable
fun PortSelectionDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onPortSelected: (Int) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val portOptions = listOf(0, 443, 123, 1194, 51820)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_port),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(portOptions) { port ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPortSelected(port) }
                                .padding(vertical = 12.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (port == 0) stringResource(R.string.settings_port_auto) else port.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textNorm,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = (port == currentPort),
                                onClick = { onPortSelected(port) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.brandNorm,
                                    unselectedColor = colors.shade60
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) {
                    Text(stringResource(id = android.R.string.cancel), color = colors.brandNorm)
                }
            }
        }
    }
}

@Composable
private fun FeatureCategory(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToSplitTunnelingMain: (() -> Unit)?,
    onNavigateToProtocol: (() -> Unit)?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Split Tunneling Tile
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_split_tunneling),
            subtitle = if (state.splitTunnelingEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            icon = Icons.AutoMirrored.Rounded.AltRoute,
            isActive = state.splitTunnelingEnabled,
            onClick = { onNavigateToSplitTunnelingMain?.invoke() }
        )

        // Protocol Tile
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_protocol),
            subtitle = "AmneziaWG",
            icon = Icons.Rounded.Security,
            isActive = true,
            onClick = { onNavigateToProtocol?.invoke() }
        )
    }
}

@Composable
fun FeatureTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        modifier = modifier.aspectRatio(1f)
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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

@Composable
fun Category(
    modifier: Modifier = Modifier,
    title: String,
    content: (@Composable ColumnScope.() -> Unit),
) {
    val colors = ProtonNextTheme.colors
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textNorm,
            modifier = modifier
                .padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
                .fillMaxWidth()
        )
    } else {
        Spacer(modifier = Modifier.height(16.dp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun SettingRowWithIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    var baseModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        baseModifier = baseModifier.clickable(onClick = onClick)
    }
    baseModifier = baseModifier.padding(vertical = 12.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.brandNorm.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textNorm
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.iconWeak.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ProtonNextTheme.colors
    SettingRowWithIcon(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.textInverted,
                    checkedTrackColor = colors.brandNorm,
                    uncheckedThumbColor = colors.shade60,
                    uncheckedTrackColor = colors.shade20,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    )
}
