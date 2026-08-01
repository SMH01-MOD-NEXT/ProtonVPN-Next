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

package ru.protonmod.next.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.ai.AiProvider
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.screens.settings.SettingsCategory
import ru.protonmod.next.ui.screens.settings.SettingRowWithIcon
import ru.protonmod.next.ui.screens.settings.SettingToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationHeader(
                    title = stringResource(R.string.ai_settings_title),
                    onBack = onBack
                )

                // Header Image
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
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = colors.brandNorm,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // AI Mode Toggle
                SettingsCategory(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(R.string.ai_mode_title)
                ) {
                    SettingToggleRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.ai_mode_title),
                        subtitle = stringResource(R.string.ai_mode_desc),
                        checked = uiState.isAiEnabled,
                        onCheckedChange = viewModel::setAiEnabled
                    )
                }

                if (uiState.isAiEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AiProviderSection(
                        selectedProvider = uiState.selectedProvider,
                        onProviderSelect = viewModel::setProvider,
                        selectedModel = uiState.selectedModel,
                        onModelSelect = viewModel::setModel,
                        apiKey = uiState.apiKey,
                        onApiKeyChange = viewModel::setApiKey,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsCategory(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.settings_api_bypass)
                    ) {
                        SettingToggleRow(
                            icon = Icons.Rounded.PublicOff,
                            title = stringResource(R.string.ai_bypass_blocks),
                            subtitle = stringResource(R.string.ai_bypass_blocks_desc),
                            checked = uiState.aiBypassBlocks,
                            onCheckedChange = viewModel::setAiBypassBlocks
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tips / Instructions
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.backgroundSecondary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = colors.brandNorm,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.ai_hint_api_key),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textNorm
                                )
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Rounded.TouchApp,
                                    contentDescription = null,
                                    tint = colors.brandNorm,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.ai_hint_long_press),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textNorm
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiProviderSection(
    selectedProvider: AiProvider,
    onProviderSelect: (AiProvider) -> Unit,
    selectedModel: String,
    onModelSelect: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    val colors = ProtonNextTheme.colors

    SettingsCategory(modifier = modifier, title = stringResource(R.string.ai_provider)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.ai_provider),
            subtitle = selectedProvider.displayName,
            onClick = { showProviderDialog = true }
        )
        
        SettingRowWithIcon(
            icon = Icons.Rounded.SettingsSuggest,
            title = stringResource(R.string.ai_model),
            subtitle = selectedModel,
            onClick = { showModelDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(stringResource(R.string.ai_api_key)) },
            placeholder = { Text(stringResource(R.string.ai_hint_api_key)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandNorm,
                unfocusedBorderColor = colors.separatorNorm,
                focusedLabelColor = colors.brandNorm,
                cursorColor = colors.brandNorm,
                unfocusedLabelColor = colors.textWeak,
                focusedTextColor = colors.textNorm,
                unfocusedTextColor = colors.textNorm
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Text(
            text = stringResource(R.string.ai_byok_desc),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textWeak,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text(stringResource(R.string.ai_provider), color = colors.textNorm) },
            text = {
                Column {
                    AiProvider.entries.forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProviderSelect(provider)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == provider,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(provider.displayName, color = colors.textNorm)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = colors.backgroundSecondary
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select Model", color = colors.textNorm) },
            text = {
                Column {
                    selectedProvider.models.forEach { model ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModelSelect(model)
                                    showModelDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModel == model,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(model, color = colors.textNorm)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = colors.backgroundSecondary
        )
    }
}
