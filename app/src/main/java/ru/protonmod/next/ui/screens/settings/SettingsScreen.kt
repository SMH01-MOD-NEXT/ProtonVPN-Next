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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
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
    onNavigateToObfuscation: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val currentTarget = MainTarget.Settings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = colors.backgroundNorm,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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

            AnimatedContent(
                targetState = uiState,
                label = "settings_state",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { state ->
                SettingsContent(
                    state = state,
                    onKillSwitchChange = viewModel::setKillSwitch,
                    onAutoConnectChange = viewModel::setAutoConnect,
                    onNotificationsChange = viewModel::setNotifications,
                    onPortChange = viewModel::setVpnPort,
                    onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                    onNavigateToObfuscation = onNavigateToObfuscation,
                    onNavigateToAbout = onNavigateToAbout
                )
            }

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
                modifier = Modifier.align(Alignment.BottomCenter)
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
    onNavigateToObfuscation: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    var showPortDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        // Features Category
        item {
            FeatureCategory(
                state = state,
                onAutoConnectChange = onAutoConnectChange,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onKillSwitchChange = onKillSwitchChange
            )
        }

        // Connection Settings
        item {
            Category(title = stringResource(R.string.settings_connection)) {
                SettingRowWithIcon(
                    icon = Icons.Rounded.SettingsEthernet,
                    title = stringResource(R.string.settings_connection_protocol),
                    subtitle = "AmneziaWG (WireGuard)",
                    onClick = null
                )
                SettingRowWithIcon(
                    icon = Icons.Rounded.Numbers,
                    title = stringResource(R.string.settings_port),
                    subtitle = state.vpnPort.toString(),
                    onClick = { showPortDialog = true }
                )
                SettingRowWithIcon(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.settings_obfuscation),
                    subtitle = stringResource(R.string.settings_obfuscation_desc),
                    onClick = onNavigateToObfuscation
                )
            }
        }

        // Privacy & Notifications
        item {
            Category(title = stringResource(R.string.settings_privacy)) {
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

@Composable
fun PortSelectionDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onPortSelected: (Int) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var portText by remember { mutableStateOf(currentPort.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_port),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { if (it.length <= 5) portText = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textNorm,
                        unfocusedTextColor = colors.textNorm,
                        focusedBorderColor = colors.brandNorm,
                        unfocusedBorderColor = colors.shade20
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.brandNorm)
                    }
                    Button(
                        onClick = { portText.toIntOrNull()?.let { onPortSelected(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCategory(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onNavigateToSplitTunnelingMain: (() -> Unit)?,
    onKillSwitchChange: (Boolean) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    // Row with two square tiles
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Auto Connect Tile
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_auto_connect),
            subtitle = if (state.autoConnectEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            icon = Icons.Rounded.Autorenew,
            isActive = state.autoConnectEnabled,
            onClick = { onAutoConnectChange(!state.autoConnectEnabled) }
        )

        // Split Tunneling
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_split_tunneling),
            subtitle = if (state.splitTunnelingEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            icon = Icons.AutoMirrored.Rounded.AltRoute,
            isActive = state.splitTunnelingEnabled,
            onClick = { onNavigateToSplitTunnelingMain?.invoke() }
        )
    }

    // Kill Switch
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingToggleRow(
            icon = Icons.Rounded.GppBad,
            title = stringResource(id = R.string.settings_kill_switch),
            subtitle = stringResource(id = R.string.settings_kill_switch_desc),
            checked = state.killSwitchEnabled,
            onCheckedChange = onKillSwitchChange
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
        modifier = modifier.aspectRatio(1f) // Makes it a perfect square
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular icon background
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
                    textAlign = TextAlign.Center
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
        // Leading Icon with circular background
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
            // Padding to align text if no icon is provided
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Title and Subtitle
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

        // Trailing Content
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
