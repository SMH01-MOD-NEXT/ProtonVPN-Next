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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

/**
 * Main Hub for Split Tunneling settings.
 * Replicates the dedicated Split Tunneling view from the original app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingMainScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToIps: () -> Unit,
    onNavigateToDomains: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    // We reuse SettingsViewModel because it already holds the splitTunneling state perfectly
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                NavigationHeader(
                    title = stringResource(R.string.settings_split_tunneling),
                    onBack = onBack
                )

                // Header Image (Replaced with a large beautiful vector icon)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(colors.brandNorm.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ProtonIcons.ArrowsSwitch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.settings_split_tunneling),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(R.string.settings_split_tunneling_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Settings Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Master Toggle
                        SettingToggleRow(
                            title = stringResource(R.string.settings_split_tunneling),
                            subtitle = if (uiState.splitTunnelingEnabled) {
                                stringResource(R.string.st_enabled_subtitle)
                            } else {
                                stringResource(R.string.st_disabled_subtitle)
                            },
                            icon = ProtonIcons.ArrowsSwitch,
                            checked = uiState.splitTunnelingEnabled,
                            onCheckedChange = { viewModel.setSplitTunneling(it) }
                        )

                        // Expanded Options (Apps and IPs)
                        AnimatedVisibility(
                            visible = uiState.splitTunnelingEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                // Mode Selection Section
                                Text(
                                    text = stringResource(R.string.settings_st_mode).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textWeak,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                                )

                                SplitTunnelingModeRow(
                                    title = stringResource(R.string.st_mode_exclude),
                                    description = stringResource(R.string.st_mode_exclude_desc),
                                    isSelected = uiState.splitTunnelingMode == "exclude",
                                    onClick = { viewModel.setSplitTunnelingMode("exclude") }
                                )

                                SplitTunnelingModeRow(
                                    title = stringResource(R.string.st_mode_include),
                                    description = stringResource(R.string.st_mode_include_desc),
                                    isSelected = uiState.splitTunnelingMode == "include",
                                    onClick = { viewModel.setSplitTunnelingMode("include") }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                val isExcludeMode = uiState.splitTunnelingMode == "exclude"

                                SettingRowWithIcon(
                                    icon = ProtonIcons.Grid3,
                                    title = stringResource(
                                        if (isExcludeMode) R.string.settings_excluded_apps 
                                        else R.string.settings_included_apps
                                    ),
                                    subtitle = pluralStringResource(
                                        R.plurals.st_apps_selected,
                                        uiState.excludedApps.size,
                                        uiState.excludedApps.size
                                    ),
                                    onClick = onNavigateToApps
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                SettingRowWithIcon(
                                    icon = ProtonIcons.Servers,
                                    title = stringResource(
                                        if (isExcludeMode) R.string.settings_excluded_ips
                                        else R.string.settings_included_ips
                                    ),
                                    subtitle = pluralStringResource(
                                        R.plurals.st_ips_selected,
                                        uiState.excludedIps.size,
                                        uiState.excludedIps.size
                                    ),
                                    onClick = onNavigateToIps
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                SettingRowWithIcon(
                                    icon = ProtonIcons.Globe,
                                    title = stringResource(
                                        if (isExcludeMode) R.string.settings_excluded_domains
                                        else R.string.settings_included_domains
                                    ),
                                    subtitle = pluralStringResource(
                                        R.plurals.st_domains_selected,
                                        uiState.excludedDomains.size,
                                        uiState.excludedDomains.size
                                    ),
                                    onClick = onNavigateToDomains
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SplitTunnelingModeRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textNorm
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.brandNorm,
                unselectedColor = colors.iconWeak
            )
        )
    }
}
