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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToCountries: (() -> Unit)? = null,
    onNavigateToProfiles: (() -> Unit)? = null,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null, // Replaced specific apps/ips navigation
    viewModel: SettingsViewModel = hiltViewModel()
) {
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
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background matching Dashboard - Fixed gradient starting from the very top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                MaterialTheme.colorScheme.background
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
                    onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain
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
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        // --- Features Category (Tiles + Kill Switch) ---
        item {
            FeatureCategory(
                state = state,
                onAutoConnectChange = onAutoConnectChange,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onKillSwitchChange = onKillSwitchChange
            )
        }

        // --- Privacy & Notifications ---
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

        // --- About ---
        item {
            Category(title = stringResource(R.string.settings_about)) {
                SettingRowWithIcon(
                    icon = null, // No icon for simple info row
                    title = stringResource(R.string.settings_version),
                    subtitle = "12.0.0", // Hardcoded as per prompt context, or use BuildConfig.VERSION_NAME
                    onClick = null
                )
            }
        }
    }
}

// --- Redesigned Components based on Original Proton App ---

@Composable
private fun FeatureCategory(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onNavigateToSplitTunnelingMain: (() -> Unit)?,
    onKillSwitchChange: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(id = R.string.settings_connection),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = modifier
            .padding(start = 12.dp, top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
    )

    // Row with two square tiles
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Auto Connect Tile
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_auto_connect),
            subtitle = if (state.autoConnectEnabled) "On" else "Off",
            icon = Icons.Rounded.Autorenew,
            isActive = state.autoConnectEnabled,
            onClick = { onAutoConnectChange(!state.autoConnectEnabled) }
        )

        // Split Tunneling Tile (Now navigates instead of toggling directly)
        FeatureTile(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.settings_split_tunneling),
            subtitle = if (state.splitTunnelingEnabled) "On" else "Off",
            icon = Icons.AutoMirrored.Rounded.AltRoute,
            isActive = state.splitTunnelingEnabled,
            onClick = { onNavigateToSplitTunnelingMain?.invoke() }
        )
    }

    // Kill Switch (Full Width Card below tiles)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Trailing Content (Arrow or Switch)
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
    SettingRowWithIcon(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}