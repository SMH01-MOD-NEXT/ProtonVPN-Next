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

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.network.byedpi.ByeDpiStrategyTester
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByeDpiTestScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()
    val context = LocalContext.current
    
    var selectedMode by remember { mutableStateOf("fast") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                    .statusBarsPadding(),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
            ) {
                NavigationHeader(
                    title = stringResource(R.string.byedpi_test_title),
                    onBack = onBack
                )

                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                // Header Icon
                Box(
                    modifier = contentModifier.padding(vertical = 32.dp),
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
                            imageVector = ProtonIcons.Bug,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.byedpi_test_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = contentModifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(contentType = "Description") {
                        Text(
                            text = stringResource(R.string.byedpi_test_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }

                    item(contentType = "ModeSelector") {
                        ModeSelectorCard(
                            selectedMode = selectedMode,
                            onSelectMode = { if (!uiState.isByeDpiTesting) selectedMode = it },
                            enabled = !uiState.isByeDpiTesting
                        )
                    }

                    item(contentType = "Progress") {
                        TestingProgressCard(
                            isTesting = uiState.isByeDpiTesting,
                            progress = uiState.byeDpiTestProgress,
                            currentStrategy = uiState.byeDpiCurrentStrategy,
                            onStart = { viewModel.startByeDpiTesting(selectedMode) },
                            onStop = { viewModel.stopByeDpiTesting() }
                        )
                    }

                    if (uiState.byeDpiResults.isNotEmpty()) {
                        item(contentType = "ResultsTitle") {
                            Text(
                                text = stringResource(R.string.byedpi_test_results_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textNorm,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(uiState.byeDpiResults, key = { it.strategy }, contentType = { "Result" }) { result ->
                            ResultItem(
                                result = result,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("ByeDPI Strategy", result.strategy)
                                    clipboard.setPrimaryClip(clip)
                                },
                                onApply = { viewModel.setByeDpiFlags(result.strategy) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeSelectorCard(
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.byedpi_mode_select),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textWeak,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.byedpi_mode_fast),
                    isSelected = selectedMode == "fast",
                    onClick = { onSelectMode("fast") },
                    enabled = enabled
                )
                ModeButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.byedpi_mode_medium),
                    isSelected = selectedMode == "medium",
                    onClick = { onSelectMode("medium") },
                    enabled = enabled
                )
                ModeButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.byedpi_mode_full),
                    isSelected = selectedMode == "full",
                    onClick = { onSelectMode("full") },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun ModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colors.brandNorm else colors.backgroundSecondary.copy(alpha = 0.3f),
        contentColor = if (isSelected) colors.backgroundNorm else colors.textNorm
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun TestingProgressCard(
    isTesting: Boolean,
    progress: Float,
    currentStrategy: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTesting) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = colors.brandNorm,
                    trackColor = colors.separatorNorm
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentStrategy,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.notificationError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_stop_test))
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(ProtonIcons.Play, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_start_test))
                }
            }
        }
    }
}

@Composable
fun ResultItem(
    result: ByeDpiStrategyTester.TestResult,
    onCopy: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.backgroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.successCount == result.totalSites) ProtonIcons.CheckmarkCircle else ProtonIcons.Bug,
                        contentDescription = null,
                        tint = if (result.successCount == result.totalSites) colors.notificationSuccess else colors.textWeak,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${result.successCount}/${result.totalSites}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (result.successCount == result.totalSites) colors.notificationSuccess else colors.textNorm
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.backgroundNorm.copy(alpha = 0.5f))
                    ) {
                        Icon(ProtonIcons.Squares, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textNorm)
                    }
                    Button(
                        onClick = onApply,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_apply), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.strategy,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textWeak,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
