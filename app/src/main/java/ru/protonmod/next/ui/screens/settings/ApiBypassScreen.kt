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

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

/**
 * Screen for configuring API Block Bypass strategies.
 * Features smart detection to disable bypass if a VPN is already active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiBypassScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToByeDpiTest: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()
    val context = LocalContext.current

    // Assuming the ViewModel exposes whether ANY VPN (ours or third-party) is active
    val isAnyVpnActive = uiState.isAnyVpnActive

    // Force disable the feature if VPN is active
    val isEffectivelyEnabled = uiState.apiBypassEnabled && !isAnyVpnActive

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient matching the unified design language
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
                    .padding(bottom = 16.dp),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
            ) {
                NavigationHeader(
                    title = stringResource(R.string.settings_api_bypass),
                    onBack = onBack
                )

                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                // Header Image/Icon
                Box(
                    modifier = contentModifier
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
                            imageVector = ProtonIcons.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.settings_api_bypass),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(R.string.settings_api_bypass_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier
                        .padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Smart Warning for active VPN
                AnimatedVisibility(
                    visible = isAnyVpnActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = contentModifier.padding(horizontal = 16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.notificationWarning.copy(alpha = 0.15f),
                        contentColor = colors.notificationWarning
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = ProtonIcons.InfoCircle,
                                contentDescription = null,
                                tint = colors.notificationWarning,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.api_bypass_vpn_detected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.notificationWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Main Settings Card
                Box(
                    modifier = contentModifier
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {

                        // Master Toggle for API Bypass
                        SettingToggleRow(
                            title = stringResource(R.string.settings_api_bypass),
                            subtitle = when {
                                isAnyVpnActive -> stringResource(R.string.api_bypass_disabled_by_vpn)
                                isEffectivelyEnabled -> stringResource(R.string.st_enabled_subtitle)
                                else -> stringResource(R.string.st_disabled_subtitle)
                            },
                            icon = ProtonIcons.Shield,
                            checked = isEffectivelyEnabled,
                            enabled = !isAnyVpnActive,
                            onCheckedChange = { viewModel.setApiBypassEnabled(it) }
                        )

                        // Expanded Strategy Options
                        AnimatedVisibility(
                            visible = isEffectivelyEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                // Section Title
                                Text(
                                    text = stringResource(R.string.api_bypass_strategy_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textWeak,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                                )

                                // Strategy 1: Netlify
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_netlify),
                                    description = stringResource(R.string.api_bypass_strategy_netlify_desc),
                                    icon = ProtonIcons.Globe,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_NETLIFY,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_NETLIFY) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.2f)
                                )

                                // Strategy 2: Cloudflare
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_cloudflare),
                                    description = stringResource(R.string.api_bypass_strategy_cloudflare_desc),
                                    icon = ProtonIcons.Globe,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_CLOUDFLARE,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_CLOUDFLARE) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.2f)
                                )

                                // Strategy: Deno
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_deno),
                                    description = stringResource(R.string.api_bypass_strategy_deno_desc),
                                    icon = ProtonIcons.Globe,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_DENO,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_DENO) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.2f)
                                )

                                // Strategy 3: Proton Mirrors (DoH)
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_mirrors),
                                    description = stringResource(R.string.api_bypass_strategy_mirrors_desc),
                                    icon = ProtonIcons.Globe,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_PROTON_MIRRORS,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_PROTON_MIRRORS) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.2f)
                                )

                                // Strategy 5: Custom Proxy (SOCKS5/HTTPS)
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_custom),
                                    description = stringResource(R.string.api_bypass_strategy_custom_desc),
                                    icon = ProtonIcons.Shield,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_CUSTOM_PROXY,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_CUSTOM_PROXY) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.2f)
                                )

                                // Strategy 6: ByeDPI (Deep Packet Inspection bypass)
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_byedpi),
                                    description = stringResource(R.string.api_bypass_strategy_byedpi_desc),
                                    icon = ProtonIcons.Shield,
                                    isSelected = uiState.apiBypassStrategy == SettingsManager.STRATEGY_BYEDPI,
                                    onClick = { viewModel.setApiBypassStrategy(SettingsManager.STRATEGY_BYEDPI) }
                                )

                                // Configuration for ByeDPI (Simplified, now navigates to a separate screen)
                                AnimatedVisibility(
                                    visible = uiState.apiBypassStrategy == SettingsManager.STRATEGY_BYEDPI,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .background(colors.backgroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        // SNI Host input
                                        SettingInputRow(
                                            label = stringResource(R.string.byedpi_sni_title),
                                            value = uiState.byeDpiSni,
                                            onValueChange = { viewModel.setByeDpiSni(it) },
                                            placeholder = "google.com"
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Flags input (Read-onlyish display)
                                        SettingInputRow(
                                            label = stringResource(R.string.byedpi_flags_label),
                                            value = uiState.byeDpiFlags,
                                            onValueChange = { viewModel.setByeDpiFlags(it) },
                                            placeholder = stringResource(R.string.byedpi_flags_none)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = onNavigateToByeDpiTest,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                                        ) {
                                            Text(stringResource(R.string.btn_open_tester))
                                        }
                                    }
                                }

                                // Configuration for Custom Proxy
                                AnimatedVisibility(
                                    visible = uiState.apiBypassStrategy == SettingsManager.STRATEGY_CUSTOM_PROXY,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .background(colors.backgroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        // Host input
                                        SettingInputRow(
                                            label = stringResource(R.string.api_proxy_host),
                                            value = uiState.apiProxyHost,
                                            onValueChange = { viewModel.setApiProxyHost(it) },
                                            placeholder = "127.0.0.1"
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Port input
                                        SettingInputRow(
                                            label = stringResource(R.string.api_proxy_port),
                                            value = uiState.apiProxyPort.toString(),
                                            onValueChange = { it.toIntOrNull()?.let { port -> viewModel.setApiProxyPort(port) } },
                                            placeholder = "1080",
                                            isNumber = true
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Proxy Type selection
                                        ProxyTypeDropdown(
                                            selectedType = uiState.apiProxyType,
                                            onTypeSelect = { viewModel.setApiProxyType(it) }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Username input
                                        SettingInputRow(
                                            label = stringResource(R.string.api_proxy_username),
                                            value = uiState.apiProxyUsername,
                                            onValueChange = { viewModel.setApiProxyUsername(it) },
                                            placeholder = "user123"
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Password input
                                        SettingInputRow(
                                            label = stringResource(R.string.api_proxy_password),
                                            value = uiState.apiProxyPassword,
                                            onValueChange = { viewModel.setApiProxyPassword(it) },
                                            placeholder = "password"
                                        )
                                    }
                                }

                                // Future strategies can be added here easily
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Configuration for Custom Proxy animated visibility end
        }
    }
}

@Composable
private fun ProxyTypeDropdown(
    selectedType: String,
    onTypeSelect: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.api_proxy_type),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textWeak,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.backgroundNorm.copy(alpha = 0.5f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (selectedType == SettingsManager.PROXY_TYPE_HTTP) 
                    stringResource(R.string.api_proxy_type_http) 
                else stringResource(R.string.api_proxy_type_socks),
                color = colors.textNorm,
                style = MaterialTheme.typography.bodyMedium
            )
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.backgroundNorm)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.api_proxy_type_socks), color = colors.textNorm) },
                    onClick = {
                        onTypeSelect(SettingsManager.PROXY_TYPE_SOCKS)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.api_proxy_type_http), color = colors.textNorm) },
                    onClick = {
                        onTypeSelect(SettingsManager.PROXY_TYPE_HTTP)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isNumber: Boolean = false
) {
    val colors = ProtonNextTheme.colors

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textWeak,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SmoothOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = colors.textWeak) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (isNumber) androidx.compose.ui.text.input.KeyboardType.Number 
                              else androidx.compose.ui.text.input.KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandNorm,
                unfocusedBorderColor = colors.separatorNorm,
                focusedTextColor = colors.textNorm,
                unfocusedTextColor = colors.textNorm,
                cursorColor = colors.brandNorm
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Reusable component for selecting a bypass strategy (works like a rich RadioButton row).
 */
@Composable
private fun StrategySelectionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.brandNorm.copy(alpha = 0.15f) else colors.backgroundNorm),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.brandNorm else colors.iconNorm,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Texts
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

        Spacer(modifier = Modifier.width(16.dp))

        // Radio indicator
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by the Row's clickable
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.brandNorm,
                unselectedColor = colors.iconWeak
            )
        )
    }
}
