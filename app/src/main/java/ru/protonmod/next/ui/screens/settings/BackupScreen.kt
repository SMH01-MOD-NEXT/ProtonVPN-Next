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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.model.BackupCategory
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonColors
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirm by remember { mutableStateOf(value = false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val colors = ProtonNextTheme.colors

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.exportToUri(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    val exportSuccessMsg = stringResource(R.string.backup_export_success)
    val importSuccessMsg = stringResource(R.string.backup_import_success)

    Box(modifier = modifier) {
        LaunchedEffect(uiState.showSuccessExport, uiState.showSuccessImport, uiState.lastError) {
            if (uiState.showSuccessExport) {
                snackbarHostState.showSnackbar(exportSuccessMsg)
                viewModel.clearMessages()
            }
            if (uiState.showSuccessImport) {
                snackbarHostState.showSnackbar(importSuccessMsg)
                viewModel.clearMessages()
            }
            uiState.lastError?.let { error ->
                snackbarHostState.showSnackbar(error)
                viewModel.clearMessages()
            }
        }

        BackupScreenContent(
            uiState = uiState,
            colors = colors,
            snackbarHostState = snackbarHostState,
            showImportConfirm = showImportConfirm,
            onDismissImportConfirm = { showImportConfirm = false },
            onConfirmImport = {
                showImportConfirm = false
                viewModel.importFromUri(pendingImportUri)
            },
            onNavigateBack = onNavigateBack,
            onExport = {
                if (!uiState.isExporting) {
                    exportLauncher.launch("proton_vpn_backup_${System.currentTimeMillis()}.json")
                }
            },
            onImport = {
                if (!uiState.isImporting) {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                }
            },
            onToggleCategory = viewModel::toggleCategory,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BackupScreenContent(
    uiState: BackupUiState,
    colors: ProtonColors,
    snackbarHostState: SnackbarHostState,
    showImportConfirm: Boolean,
    onDismissImportConfirm: () -> Unit,
    onConfirmImport: () -> Unit,
    onNavigateBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleCategory: (BackupCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.backgroundNorm,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (uiState.selectedCategories.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        SmallFloatingActionButton(
                            onClick = onImport,
                            containerColor = colors.backgroundSecondary.copy(alpha = 0.85f),
                            contentColor = colors.brandNorm,
                        ) {
                            Icon(ProtonIcons.ArrowDownLine, contentDescription = null)
                        }

                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.backup_export)) },
                            icon = { Icon(ProtonIcons.ArrowUpLine, contentDescription = null) },
                            onClick = onExport,
                            containerColor = colors.brandNorm,
                            contentColor = colors.textInverted
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        .padding(bottom = 16.dp)
                ) {
                    NavigationHeader(
                        title = stringResource(R.string.backup_title),
                        onBack = onNavigateBack
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
                                imageVector = ProtonIcons.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = colors.brandNorm
                            )
                        }
                    }

                    // Title
                    Text(
                        text = stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = stringResource(R.string.backup_export_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        SettingsCategory(title = stringResource(R.string.backup_categories)) {
                            BackupCategory.entries.forEach { category ->
                                if (category != BackupCategory.SENTRY_ANALYTICS || BuildConfig.SENTRY_ENABLED) {
                                    SettingToggleRow(
                                        title = getCategoryName(category),
                                        icon = getCategoryIcon(category),
                                        checked = uiState.selectedCategories.contains(category),
                                        onCheckedChange = { onToggleCategory(category) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(140.dp))
                }
            }
        }

        if (showImportConfirm) {
            AlertDialog(
                onDismissRequest = onDismissImportConfirm,
                title = { Text(stringResource(R.string.backup_import), color = colors.textNorm) },
                text = { Text(stringResource(R.string.backup_import_confirm), color = colors.textWeak) },
                confirmButton = {
                    TextButton(onClick = onConfirmImport) {
                        Text(stringResource(android.R.string.ok), color = colors.brandNorm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissImportConfirm) {
                        Text(stringResource(R.string.btn_cancel), color = colors.textWeak)
                    }
                },
                containerColor = colors.backgroundSecondary
            )
        }
    }
}

@Composable
private fun getCategoryIcon(category: BackupCategory): ImageVector {
    return when (category) {
        BackupCategory.GENERAL_SETTINGS -> ProtonIcons.CogWheel
        BackupCategory.OBFUSCATION -> ProtonIcons.Shield
        BackupCategory.API_BYPASS -> ProtonIcons.Cloud
        BackupCategory.PROFILES -> ProtonIcons.FileLines
        BackupCategory.RECENT_CONNECTIONS -> ProtonIcons.ClockRotateLeft
        BackupCategory.QUICK_CONNECT -> ProtonIcons.Bolt
        BackupCategory.SPLIT_TUNNELING -> ProtonIcons.ArrowsSwitch
        BackupCategory.VPN_PORT -> ProtonIcons.ListNumbers
        BackupCategory.DNS -> ProtonIcons.Servers
        BackupCategory.SPOOF_COUNTRY -> ProtonIcons.Globe
        BackupCategory.OTA_UPDATES -> ProtonIcons.ArrowDownCircle
        BackupCategory.SENTRY_ANALYTICS -> ProtonIcons.ChartLine
    }
}

@Composable
private fun getCategoryName(category: BackupCategory): String {
    return when (category) {
        BackupCategory.GENERAL_SETTINGS -> stringResource(R.string.backup_cat_general)
        BackupCategory.OBFUSCATION -> stringResource(R.string.backup_cat_obfuscation)
        BackupCategory.API_BYPASS -> stringResource(R.string.backup_cat_api_bypass)
        BackupCategory.PROFILES -> stringResource(R.string.backup_cat_profiles)
        BackupCategory.RECENT_CONNECTIONS -> stringResource(R.string.backup_cat_recent)
        BackupCategory.QUICK_CONNECT -> stringResource(R.string.backup_cat_quick_connect)
        BackupCategory.SPLIT_TUNNELING -> stringResource(R.string.backup_cat_split_tunneling)
        BackupCategory.VPN_PORT -> stringResource(R.string.backup_cat_vpn_port)
        BackupCategory.DNS -> stringResource(R.string.backup_cat_dns)
        BackupCategory.SPOOF_COUNTRY -> stringResource(R.string.backup_cat_spoof_country)
        BackupCategory.OTA_UPDATES -> stringResource(R.string.backup_cat_ota)
        BackupCategory.SENTRY_ANALYTICS -> stringResource(R.string.backup_cat_sentry)
    }
}
