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
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.screens.settings.SettingRowWithIcon
import ru.protonmod.next.ui.screens.settings.SettingToggleRow
import ru.protonmod.next.ui.screens.settings.SettingsCategory
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    onNavigateToApiBypass: () -> Unit,
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
                            imageVector = ProtonIcons.MagicProtonWand,
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
                        icon = ProtonIcons.MagicProtonWand,
                        title = stringResource(R.string.ai_mode_title),
                        subtitle = stringResource(R.string.ai_mode_desc),
                        checked = uiState.isAiEnabled,
                        onCheckedChange = viewModel::setAiEnabled
                    )
                }

                if (uiState.isAiEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AiProviderSection(
                        state = uiState,
                        onProviderSelect = viewModel::setProvider,
                        onModelSelect = viewModel::setModel,
                        onApiKeyChange = viewModel::setApiKey,
                        onRefreshModels = viewModel::refreshModels,
                        onSaveCustomProvider = viewModel::saveCustomProvider,
                        onDeleteCustomProvider = viewModel::deleteCustomProvider,
                        onClearFormError = viewModel::clearFormError,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsCategory(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.settings_api_bypass)
                    ) {
                        SettingToggleRow(
                            icon = ProtonIcons.CircleSlash,
                            title = stringResource(R.string.ai_bypass_blocks),
                            subtitle = stringResource(R.string.ai_bypass_blocks_desc),
                            checked = uiState.aiBypassBlocks,
                            onCheckedChange = viewModel::setAiBypassBlocks
                        )
                        SettingRowWithIcon(
                            icon = ProtonIcons.ArrowsSwitch,
                            title = stringResource(R.string.api_bypass_strategy_custom),
                            subtitle = stringResource(R.string.api_bypass_strategy_custom_desc),
                            onClick = onNavigateToApiBypass
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
                                    ProtonIcons.Key,
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
                                    ProtonIcons.SwipeLeft,
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
