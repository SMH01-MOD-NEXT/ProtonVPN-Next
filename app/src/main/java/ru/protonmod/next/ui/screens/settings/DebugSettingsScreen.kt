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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()
    var showNukeConfirm by remember { mutableStateOf(false) }
    var showServerSelect by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(uiState.message) {
            uiState.message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.backgroundNorm,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Background gradient decoration (immersive)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Red.copy(alpha = 0.15f), // Red highlight for Debug
                                    colors.backgroundNorm.copy(alpha = 0.1f),
                                    colors.backgroundNorm
                                )
                            )
                        )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                    item(contentType = "Header") {
                        NavigationHeader(
                            title = stringResource(R.string.debug_title),
                            onBack = onBack
                        )

                        // Header Icon
                        Box(
                            modifier = contentModifier.padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ProtonIcons.Bug,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Red
                                )
                            }
                        }

                        // Title
                        Text(
                            text = stringResource(R.string.debug_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            textAlign = TextAlign.Center,
                            modifier = contentModifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            text = stringResource(R.string.debug_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = contentModifier.padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Session & Certificate Info
                    item(contentType = "DebugSection") {
                        Column(modifier = contentModifier.padding(horizontal = 16.dp)) {
                            DebugSection(title = stringResource(R.string.debug_session_header)) {
                                uiState.session?.let { session ->
                                    DebugInfoRow(stringResource(R.string.debug_user_id), session.userId)
                                    DebugInfoRow(stringResource(R.string.debug_tier), when (session.userTier) {
                                        1 -> stringResource(R.string.debug_tier_basic)
                                        2 -> stringResource(R.string.debug_tier_plus)
                                        else -> stringResource(R.string.debug_tier_free)
                                    })
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = colors.separatorNorm.copy(alpha = 0.3f)
                                    )

                                    val idLabel =
                                        stringResource(R.string.debug_cert_id, "").trim().removeSuffix(":")
                                            .trim()
                                    val issuedLabel = stringResource(R.string.debug_cert_issued, "").trim()
                                        .removeSuffix(":").trim()
                                    val expiresLabel = stringResource(R.string.debug_cert_expires, "").trim()
                                        .removeSuffix(":").trim()

                                    DebugInfoRow(idLabel, session.sessionId)
                                    uiState.certIssued?.let { DebugInfoRow(issuedLabel, it) }
                                    uiState.certExpires?.let { DebugInfoRow(expiresLabel, it) }

                                    Button(
                                        onClick = { viewModel.forceRefreshCertificate() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !uiState.isLoading
                                    ) {
                                        Icon(ProtonIcons.ArrowsRotate, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.debug_btn_refresh_cert))
                                    }

                                    Button(
                                        onClick = { viewModel.forceRefreshSession() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm.copy(alpha = 0.8f)),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !uiState.isLoading
                                    ) {
                                        Icon(ProtonIcons.ArrowsRotate, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.debug_btn_refresh_session))
                                    }

                                    Button(
                                        onClick = { viewModel.simulateExpiredCertificate() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.notificationError.copy(alpha = 0.8f)),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !uiState.isLoading
                                    ) {
                                        Icon(ProtonIcons.Bug, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.debug_btn_simulate_expired_cert))
                                    }
                                } ?: Text(
                                    stringResource(R.string.debug_no_session),
                                    color = colors.textWeak,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // Exports
                    item(contentType = "DebugSection") {
                        Column(modifier = contentModifier.padding(horizontal = 16.dp)) {
                            DebugSection(title = stringResource(R.string.debug_exports_header)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DebugActionRow(
                                        icon = ProtonIcons.ClockRotateLeft,
                                        title = stringResource(R.string.debug_btn_export_logs),
                                        onClick = { viewModel.exportLogs() }
                                    )
                                    DebugActionRow(
                                        icon = ProtonIcons.ArrowDownLine,
                                        title = stringResource(R.string.debug_btn_export_config),
                                        onClick = { showServerSelect = true }
                                    )
                                    DebugActionRow(
                                        icon = ProtonIcons.Squares,
                                        title = stringResource(R.string.debug_btn_export_session),
                                        onClick = { showExportConfirm = true }
                                    )
                                    DebugActionRow(
                                        icon = ProtonIcons.ArrowInToRectangle,
                                        title = stringResource(R.string.debug_btn_import_session),
                                        onClick = { showImportDialog = true }
                                    )
                                    DebugActionRow(
                                        icon = ProtonIcons.Globe,
                                        title = stringResource(R.string.debug_btn_fetch_domains),
                                        onClick = { viewModel.fetchAvailableDomains() }
                                    )
                                }
                            }
                        }
                    }

                    // Device Info
                    item(contentType = "DebugSection") {
                        Column(modifier = contentModifier.padding(horizontal = 16.dp)) {
                            DebugSection(title = stringResource(R.string.debug_device_header)) {
                                Text(
                                    text = viewModel.getDeviceInfo(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textWeak,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // Sentry Tests
                    if (BuildConfig.SENTRY_ENABLED) {
                        item(contentType = "DebugSection") {
                            Column(modifier = contentModifier.padding(horizontal = 16.dp)) {
                                DebugSection(
                                    title = stringResource(R.string.debug_sentry_header),
                                    titleColor = Color.Magenta
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_fake_crash),
                                            onClick = { viewModel.triggerJavaCrash() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_crash_java),
                                            onClick = { viewModel.triggerJavaCrash() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_crash_native),
                                            onClick = { viewModel.triggerNativeCrash() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_arithmetic_error),
                                            onClick = { viewModel.triggerArithmeticException() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_null_pointer),
                                            onClick = { viewModel.triggerNullPointer() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Bug,
                                            title = stringResource(R.string.debug_btn_background_crash),
                                            onClick = { viewModel.triggerBackgroundCrash() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.ClockRotateLeft,
                                            title = stringResource(R.string.debug_btn_anr),
                                            onClick = { viewModel.triggerAnr() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.Broom,
                                            title = stringResource(R.string.debug_btn_oom),
                                            onClick = { viewModel.triggerOom() }
                                        )
                                        DebugActionRow(
                                            icon = ProtonIcons.InfoCircle,
                                            title = stringResource(R.string.debug_btn_capture_exception),
                                            onClick = { viewModel.captureNonFatal() }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Danger Zone
                    item(contentType = "DebugSection") {
                        Column(modifier = contentModifier.padding(horizontal = 16.dp)) {
                            DebugSection(
                                title = stringResource(R.string.debug_danger_header),
                                titleColor = colors.notificationError
                            ) {
                                Button(
                                    onClick = { showNukeConfirm = true },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.notificationError),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(ProtonIcons.Broom, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.debug_btn_nuke))
                                }
                            }
                        }
                    }
                }

                // Nuke Confirmation Dialog
                if (showNukeConfirm) {
                    AlertDialog(
                        onDismissRequest = { showNukeConfirm = false },
                        title = { Text(stringResource(R.string.debug_btn_nuke)) },
                        text = { Text(stringResource(R.string.debug_nuke_confirm)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNukeConfirm = false
                                    viewModel.nukeEverything()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.notificationError)
                            ) {
                                Text(stringResource(R.string.btn_disconnect)) // Using "Disconnect" as "Confirm" if better string not found, or just HARD reset
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNukeConfirm = false }) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        }
                    )
                }

                // Server Selection for Config Export
                if (showServerSelect) {
                    ModalBottomSheet(
                        onDismissRequest = { showServerSelect = false },
                        containerColor = colors.backgroundNorm,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.iconWeak) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                            Text(
                                text = stringResource(R.string.debug_select_server),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textNorm,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                                items(uiState.servers) { server ->
                                    ListItem(
                                        onClick = {
                                            viewModel.exportConfig(server)
                                            showServerSelect = false
                                        },
                                        supportingContent = { Text(server.exitCountry, color = colors.textWeak) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    ) {
                                        Text(server.name, color = colors.textNorm)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showExportConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExportConfirm = false },
                        title = { Text(stringResource(R.string.debug_export_session_title)) },
                        text = { Text(stringResource(R.string.debug_export_session_msg)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.exportSession()
                                    showExportConfirm = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = colors.brandNorm)
                            ) {
                                Text(stringResource(R.string.debug_btn_copy))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExportConfirm = false }) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        },
                        containerColor = colors.backgroundSecondary,
                        titleContentColor = colors.textNorm,
                        textContentColor = colors.textWeak
                    )
                }

                if (showImportDialog) {
                    AlertDialog(
                        onDismissRequest = { showImportDialog = false },
                        title = { Text(stringResource(R.string.debug_btn_import_session)) },
                        text = {
                            Column {
                                Text(
                                    text = stringResource(R.string.debug_import_session_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.notificationError,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                SmoothOutlinedTextField(
                                    value = importJson,
                                    onValueChange = { importJson = it },
                                    label = { Text(stringResource(R.string.hint_session_json)) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.brandNorm,
                                        unfocusedBorderColor = colors.shade20,
                                        focusedTextColor = colors.textNorm,
                                        unfocusedTextColor = colors.textNorm
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (importJson.isNotBlank()) {
                                        viewModel.importSession(importJson)
                                        showImportDialog = false
                                    }
                                },
                                enabled = importJson.isNotBlank()
                            ) {
                                Text(stringResource(R.string.btn_import))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showImportDialog = false }) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        },
                        containerColor = colors.backgroundSecondary,
                        titleContentColor = colors.textNorm,
                        textContentColor = colors.textWeak
                    )
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        ExpressiveCircularProgressIndicator(color = colors.brandNorm)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    titleColor: Color = ProtonNextTheme.colors.brandNorm,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ProtonNextTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.3f, shadowElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun DebugInfoRow(label: String, value: String) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textWeak)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textNorm, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DebugActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.brandNorm)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textNorm)
    }
}
