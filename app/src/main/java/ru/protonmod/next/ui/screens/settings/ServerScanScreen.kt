/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import ru.protonmod.next.R
import ru.protonmod.next.data.network.probe.AvailabilityBadge
import ru.protonmod.next.data.network.probe.ServerAvailability
import ru.protonmod.next.data.network.probe.ServerProbeDetail
import ru.protonmod.next.data.network.probe.ServerProbeResult
import ru.protonmod.next.data.network.probe.ServerScanError
import ru.protonmod.next.data.network.probe.ServerScanObfuscationMode
import ru.protonmod.next.data.network.probe.ServerScanPhase
import ru.protonmod.next.data.network.probe.ServerScanPortMode
import ru.protonmod.next.data.network.probe.ServerScanPreferences
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.screens.countries.AvailabilityBadgeIcon
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet

private enum class ScanPicker { COUNTRY, SERVER }

private data class PickerOption(val id: String, val title: String, val subtitle: String?)

@Composable
fun ServerScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerScanViewModel = hiltViewModel(),
) {
    val colors = ProtonNextTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tablet = isTablet()
    val editable = !state.scan.isRunning
    var picker by remember { mutableStateOf<ScanPicker?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            colors.brandNorm.copy(alpha = 0.25f),
                            colors.backgroundNorm.copy(alpha = 0.1f),
                            colors.backgroundNorm,
                        )
                    )
                )
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                horizontalAlignment = if (tablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val content = if (tablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()
                item(contentType = "Header") {
                    ScanHeader(onBack, content)
                }
                item(contentType = "Target") {
                    TargetSection(state, editable, content, viewModel::setTarget) { picker = it }
                }
                item(contentType = "Progress") {
                    ProgressSection(state, content, viewModel::start, viewModel::stop)
                }
                if (state.target == ServerScanTarget.SERVER && state.selectedServerResults.isNotEmpty()) {
                    item(contentType = "ServerResults") {
                        ServerResultsSection(state.selectedServerResults, content)
                    }
                }
                item(contentType = "Parameters") {
                    ParametersSection(
                        state.preferences, editable, content,
                        viewModel::setThreads, viewModel::setTimeout, viewModel::setAttempts,
                    )
                }
                item(contentType = "Ports") {
                    PortsSection(state.preferences, editable, content, viewModel::setPortMode, viewModel::setCustomPort)
                }
                item(contentType = "Obfuscation") {
                    ObfuscationSection(state.preferences.obfuscationMode, editable, content, viewModel::setObfuscationMode)
                }
                item(contentType = "Checks") {
                    ChecksSection(state.preferences, editable, content, viewModel::setTcpCheck, viewModel::setDataCheck)
                }
                item(contentType = "Results") {
                    ResultsSection(state, content, viewModel::clearResults)
                }
                item(contentType = "Note") {
                    Text(
                        stringResource(R.string.server_scan_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        modifier = content.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }

    when (picker) {
        ScanPicker.COUNTRY -> {
            val context = LocalContext.current
            val codes = state.countryCodes
            val options = remember(codes) {
                codes.map { code -> PickerOption(code, CountryUtils.getCountryName(context, code), code) }
                    .sortedBy { it.title }
            }
            PickerDialog(
                title = stringResource(R.string.server_scan_choose_country),
                options = options,
                selectedId = state.selectedCountry,
                onSelect = { code ->
                    viewModel.selectCountry(code)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
        ScanPicker.SERVER -> {
            val servers = state.countryServers
            val options = remember(servers) {
                servers.map { PickerOption(it.id, it.name, it.localizedCity ?: it.city) }
            }
            PickerDialog(
                title = stringResource(R.string.server_scan_choose_server),
                options = options,
                selectedId = state.selectedServer?.id,
                onSelect = { id ->
                    viewModel.selectServer(id)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun ScanHeader(onBack: () -> Unit, modifier: Modifier) {
    val colors = ProtonNextTheme.colors
    NavigationHeader(stringResource(R.string.server_scan_title), onBack)
    Box(modifier.padding(top = 24.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProtonIcons.Magnifier, contentDescription = null, tint = colors.brandNorm, modifier = Modifier.size(58.dp))
        }
    }
    Text(
        stringResource(R.string.server_scan_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = colors.textNorm,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.server_scan_description),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textWeak,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 32.dp),
    )
}

@Composable
private fun TargetSection(
    state: ServerScanUiState,
    editable: Boolean,
    modifier: Modifier,
    onTarget: (ServerScanTarget) -> Unit,
    onPick: (ScanPicker) -> Unit,
) {
    val context = LocalContext.current
    ScanSection(stringResource(R.string.server_scan_section_target), modifier) {
        ScanRadioRow(
            R.string.server_scan_target_all, R.string.server_scan_target_all_desc,
            state.target == ServerScanTarget.ALL, editable,
        ) { onTarget(ServerScanTarget.ALL) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_target_country, R.string.server_scan_target_country_desc,
            state.target == ServerScanTarget.COUNTRY, editable,
        ) { onTarget(ServerScanTarget.COUNTRY) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_target_server, R.string.server_scan_target_server_desc,
            state.target == ServerScanTarget.SERVER, editable,
        ) { onTarget(ServerScanTarget.SERVER) }
        AnimatedVisibility(
            visible = state.target != ServerScanTarget.ALL,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                val countryName = state.selectedCountry?.let { code ->
                    remember(code) { CountryUtils.getCountryName(context, code) }
                }
                ScanDivider()
                ScanValueRow(
                    stringResource(R.string.server_scan_choose_country),
                    countryName ?: stringResource(R.string.server_scan_not_selected),
                    editable,
                ) { onPick(ScanPicker.COUNTRY) }
                if (state.target == ServerScanTarget.SERVER) {
                    ScanDivider()
                    ScanValueRow(
                        stringResource(R.string.server_scan_choose_server),
                        state.selectedServer?.name ?: stringResource(R.string.server_scan_not_selected),
                        editable && state.selectedCountry != null,
                    ) { onPick(ScanPicker.SERVER) }
                }
            }
        }
    }
}


@Composable
private fun ProgressSection(
    state: ServerScanUiState,
    modifier: Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val scan = state.scan
    ScanSection(title = stringResource(R.string.server_scan_section_progress), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(scan.phase.labelRes()),
                    color = colors.textNorm,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (scan.total > 0) {
                    Text(
                        text = stringResource(R.string.server_scan_progress, scan.completed, scan.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                    )
                }
            }
            if (scan.isRunning) {
                val barModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                if (scan.total > 0) {
                    LinearProgressIndicator(
                        progress = { (scan.completed.toFloat() / scan.total).coerceIn(0f, 1f) },
                        modifier = barModifier,
                        color = colors.brandNorm,
                        trackColor = colors.brandNorm.copy(alpha = 0.15f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = barModifier,
                        color = colors.brandNorm,
                        trackColor = colors.brandNorm.copy(alpha = 0.15f),
                    )
                }
            }
            if (scan.phase != ServerScanPhase.IDLE) {
                ScanCounters(scan.available, scan.blocked, scan.unknown)
                if (scan.awaitingConfirmation > 0) {
                    SmallNote(stringResource(R.string.server_scan_awaiting_count, scan.awaitingConfirmation))
                }
                if (scan.skippedActive > 0) {
                    SmallNote(stringResource(R.string.server_scan_skipped_active, scan.skippedActive))
                }
            }
            scan.error?.let { error ->
                Text(
                    text = stringResource(error.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.notificationError,
                )
            }
            if (scan.cancelled && scan.error == null) {
                SmallNote(stringResource(R.string.server_scan_cancelled))
            }
            if (scan.isRunning) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.server_scan_stop),
                        color = colors.notificationError,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandNorm,
                        contentColor = colors.textInverted,
                    ),
                ) {
                    Text(text = stringResource(R.string.server_scan_start), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ServerResultsSection(results: List<ServerProbeResult>, modifier: Modifier) {
    val colors = ProtonNextTheme.colors
    ScanSection(title = stringResource(R.string.server_scan_section_server_results), modifier = modifier) {
        results.forEachIndexed { index, result ->
            if (index > 0) ScanDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvailabilityBadgeIcon(result.availability.toBadge())
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.endpoint ?: result.physicalServerId,
                        color = colors.textNorm,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(result.detail.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                    )
                }
                result.latencyMs?.let { latency ->
                    Text(
                        text = stringResource(R.string.server_scan_milliseconds, latency),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametersSection(
    preferences: ServerScanPreferences,
    editable: Boolean,
    modifier: Modifier,
    onThreads: (Int) -> Unit,
    onTimeout: (Int) -> Unit,
    onAttempts: (Int) -> Unit,
) {
    ScanSection(title = stringResource(R.string.server_scan_section_parameters), modifier = modifier) {
        ScanSlider(
            title = stringResource(R.string.server_scan_threads),
            description = stringResource(R.string.server_scan_threads_desc),
            value = preferences.threads,
            range = ServerScanPreferences.THREADS_RANGE,
            step = 1,
            enabled = editable,
            valueText = { it.toString() },
            onChange = onThreads,
        )
        ScanDivider()
        ScanSlider(
            title = stringResource(R.string.server_scan_timeout),
            description = null,
            value = preferences.timeoutMs,
            range = ServerScanPreferences.TIMEOUT_RANGE_MS,
            step = 100,
            enabled = editable,
            valueText = { stringResource(R.string.server_scan_milliseconds, it) },
            onChange = onTimeout,
        )
        ScanDivider()
        ScanSlider(
            title = stringResource(R.string.server_scan_attempts),
            description = stringResource(R.string.server_scan_attempts_desc),
            value = preferences.attempts,
            range = ServerScanPreferences.ATTEMPTS_RANGE,
            step = 1,
            enabled = editable,
            valueText = { it.toString() },
            onChange = onAttempts,
        )
    }
}


@Composable
private fun PortsSection(
    preferences: ServerScanPreferences,
    editable: Boolean,
    modifier: Modifier,
    onMode: (ServerScanPortMode) -> Unit,
    onCustomPort: (Int) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    ScanSection(title = stringResource(R.string.server_scan_section_ports), modifier = modifier) {
        ScanRadioRow(
            R.string.server_scan_same_as_connection, R.string.server_scan_port_connection_desc,
            preferences.portMode == ServerScanPortMode.CONNECTION, editable,
        ) { onMode(ServerScanPortMode.CONNECTION) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_port_standard, R.string.server_scan_port_standard_desc,
            preferences.portMode == ServerScanPortMode.ALL_STANDARD, editable,
        ) { onMode(ServerScanPortMode.ALL_STANDARD) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_port_custom, null,
            preferences.portMode == ServerScanPortMode.CUSTOM, editable,
        ) { onMode(ServerScanPortMode.CUSTOM) }
        AnimatedVisibility(
            visible = preferences.portMode == ServerScanPortMode.CUSTOM,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            var text by remember { mutableStateOf(preferences.customPort.toString()) }
            var focused by remember { mutableStateOf(false) }
            // While the field is being edited, saved values that arrive out of order
            // never overwrite what the user is typing.
            LaunchedEffect(preferences.customPort, focused) {
                if (!focused && text.toIntOrNull() != preferences.customPort) {
                    text = preferences.customPort.toString()
                }
            }
            val valid = text.toIntOrNull()?.let { it in ServerScanPreferences.PORT_RANGE } == true
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(5)
                    text = digits
                    digits.toIntOrNull()?.takeIf { it in ServerScanPreferences.PORT_RANGE }?.let(onCustomPort)
                },
                enabled = editable,
                singleLine = true,
                isError = !valid,
                label = { Text(stringResource(R.string.server_scan_port_custom_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = scanFieldColors(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textNorm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun ObfuscationSection(
    mode: ServerScanObfuscationMode,
    editable: Boolean,
    modifier: Modifier,
    onMode: (ServerScanObfuscationMode) -> Unit,
) {
    ScanSection(title = stringResource(R.string.server_scan_section_obfuscation), modifier = modifier) {
        ScanRadioRow(
            R.string.server_scan_same_as_connection, R.string.server_scan_obfuscation_connection_desc,
            mode == ServerScanObfuscationMode.CONNECTION, editable,
        ) { onMode(ServerScanObfuscationMode.CONNECTION) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_obfuscation_always, R.string.server_scan_obfuscation_always_desc,
            mode == ServerScanObfuscationMode.ALWAYS, editable,
        ) { onMode(ServerScanObfuscationMode.ALWAYS) }
        ScanDivider()
        ScanRadioRow(
            R.string.server_scan_obfuscation_never, R.string.server_scan_obfuscation_never_desc,
            mode == ServerScanObfuscationMode.NEVER, editable,
        ) { onMode(ServerScanObfuscationMode.NEVER) }
    }
}

@Composable
private fun ChecksSection(
    preferences: ServerScanPreferences,
    editable: Boolean,
    modifier: Modifier,
    onTcpCheck: (Boolean) -> Unit,
    onDataCheck: (Boolean) -> Unit,
) {
    ScanSection(title = stringResource(R.string.server_scan_section_checks), modifier = modifier) {
        ScanToggle(
            R.string.server_scan_tcp_check, R.string.server_scan_tcp_check_desc,
            preferences.tcpCheck, editable, onTcpCheck,
        )
        ScanDivider()
        ScanToggle(
            R.string.server_scan_data_check, R.string.server_scan_data_check_desc,
            preferences.dataCheck, editable, onDataCheck,
        )
    }
}

@Composable
private fun ResultsSection(state: ServerScanUiState, modifier: Modifier, onClear: () -> Unit) {
    val colors = ProtonNextTheme.colors
    val summary = state.summary
    ScanSection(title = stringResource(R.string.server_scan_section_results), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (summary.total == 0) {
                Text(
                    text = stringResource(R.string.server_scan_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                )
            } else {
                ScanCounters(summary.available, summary.blocked, summary.unknown)
            }
            summary.lastCheckedAt?.let { checkedAt ->
                val formatted = remember(checkedAt) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checkedAt))
                }
                SmallNote(stringResource(R.string.server_scan_last_check, formatted))
            }
            OutlinedButton(
                onClick = onClear,
                enabled = !state.scan.isRunning && summary.total > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    ProtonIcons.Trash,
                    contentDescription = null,
                    tint = colors.textNorm,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.server_scan_clear_results), color = colors.textNorm)
            }
        }
    }
}


@Composable
private fun PickerDialog(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val trimmed = query.trim()
        val unique = options.distinctBy { it.id }
        if (trimmed.isEmpty()) {
            unique
        } else {
            unique.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.subtitle?.contains(trimmed, ignoreCase = true) == true
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.backgroundNorm,
        title = { Text(text = title, color = colors.textNorm) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.server_scan_search)) },
                    leadingIcon = {
                        Icon(
                            ProtonIcons.Magnifier,
                            contentDescription = null,
                            tint = colors.iconWeak,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = scanFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.server_scan_nothing_found),
                        color = colors.textWeak,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(filtered, key = { it.id }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = option.id == selectedId,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(option.id) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        color = colors.textNorm,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    option.subtitle?.let { subtitle ->
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textWeak,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (option.id == selectedId) {
                                    Icon(
                                        ProtonIcons.Checkmark,
                                        contentDescription = null,
                                        tint = colors.brandNorm,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel), color = colors.brandNorm)
            }
        },
    )
}

@Composable
private fun ScanSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ProtonNextTheme.colors
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textWeak,
            modifier = Modifier.padding(start = 8.dp),
        )
        Column(
            Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun ScanRadioRow(
    @StringRes title: Int,
    @StringRes description: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), color = colors.textNorm, fontWeight = FontWeight.Medium)
            description?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                )
            }
        }
    }
}

@Composable
private fun ScanToggle(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), color = colors.textNorm, fontWeight = FontWeight.Medium)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textInverted,
                checkedTrackColor = colors.brandNorm,
                uncheckedThumbColor = colors.shade60,
                uncheckedTrackColor = colors.shade20,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ScanValueRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = colors.textNorm, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = colors.brandNorm,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).widthIn(max = 200.dp),
        )
        Icon(
            ProtonIcons.ChevronRight,
            contentDescription = null,
            tint = colors.iconWeak.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp).size(20.dp),
        )
    }
}


@Composable
private fun ScanSlider(
    title: String,
    description: String?,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    valueText: @Composable (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val intervals = (range.last - range.first) / step
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colors.textNorm,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText(snapToStep(position, range, step)),
                color = colors.brandNorm,
                fontWeight = FontWeight.Bold,
            )
        }
        description?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
        }
        val steps = if (intervals in 1..10) intervals - 1 else 0
        val trackRange = range.first.toFloat()..range.last.toFloat()
        val sliderState = remember(steps, trackRange) { SliderState(position, steps, trackRange) }
        sliderState.value = position
        Slider(
            state = sliderState,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(snapToStep(position, range, step)) },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.brandNorm,
                activeTrackColor = colors.brandNorm,
                inactiveTrackColor = colors.brandNorm.copy(alpha = 0.2f),
            ),
        )
    }
}

private fun snapToStep(position: Float, range: IntRange, step: Int): Int =
    (range.first + ((position - range.first) / step).roundToInt() * step).coerceIn(range)

@Composable
private fun ScanCounters(available: Int, blocked: Int, unknown: Int) {
    val colors = ProtonNextTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CounterLine(colors.notificationSuccess, stringResource(R.string.server_scan_available_count, available))
        CounterLine(colors.notificationError, stringResource(R.string.server_scan_blocked_count, blocked))
        CounterLine(colors.notificationWarning, stringResource(R.string.server_scan_unknown_count, unknown))
    }
}

@Composable
private fun CounterLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = ProtonNextTheme.colors.textNorm)
    }
}

@Composable
private fun SmallNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = ProtonNextTheme.colors.textWeak)
}

@Composable
private fun ScanDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = ProtonNextTheme.colors.separatorNorm.copy(alpha = 0.5f),
    )
}

@Composable
private fun scanFieldColors(): TextFieldColors {
    val colors = ProtonNextTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.brandNorm,
        unfocusedBorderColor = colors.separatorNorm,
        focusedLabelColor = colors.brandNorm,
        cursorColor = colors.brandNorm,
        focusedTextColor = colors.textNorm,
        unfocusedTextColor = colors.textNorm,
    )
}

private fun ServerAvailability.toBadge(): AvailabilityBadge = when (this) {
    ServerAvailability.AVAILABLE -> AvailabilityBadge.ALL_AVAILABLE
    ServerAvailability.BLOCKED -> AvailabilityBadge.ALL_BLOCKED
    ServerAvailability.UNKNOWN -> AvailabilityBadge.UNKNOWN
}

@StringRes
private fun ServerScanPhase.labelRes(): Int = when (this) {
    ServerScanPhase.IDLE -> R.string.server_scan_phase_idle
    ServerScanPhase.PREPARING -> R.string.server_scan_phase_preparing
    ServerScanPhase.CERTIFICATE -> R.string.server_scan_phase_certificate
    ServerScanPhase.CHECKING_NETWORK -> R.string.server_scan_phase_checking_network
    ServerScanPhase.SCANNING -> R.string.server_scan_phase_scanning
    ServerScanPhase.CONFIRMING -> R.string.server_scan_phase_confirming
    ServerScanPhase.FINISHED -> R.string.server_scan_phase_finished
}

@StringRes
private fun ServerScanError.labelRes(): Int = when (this) {
    ServerScanError.NOT_LOGGED_IN -> R.string.server_scan_error_not_logged_in
    ServerScanError.NO_SERVERS -> R.string.server_scan_error_no_servers
    ServerScanError.ONLY_ACTIVE_SERVER -> R.string.server_scan_error_only_active_server
    ServerScanError.NO_NETWORK -> R.string.server_scan_error_no_network
    ServerScanError.NETWORK_LOST -> R.string.server_scan_error_network_lost
    ServerScanError.CERTIFICATE -> R.string.server_scan_error_certificate
    ServerScanError.VPN_BUSY -> R.string.server_scan_error_vpn_busy
    ServerScanError.OBFUSCATION_CONFIG -> R.string.server_scan_error_obfuscation_config
    ServerScanError.FAILED -> R.string.server_scan_error_failed
}

@StringRes
private fun ServerProbeDetail.labelRes(): Int = when (this) {
    ServerProbeDetail.HANDSHAKE_VERIFIED -> R.string.server_scan_detail_handshake_verified
    ServerProbeDetail.DATA_VERIFIED -> R.string.server_scan_detail_data_verified
    ServerProbeDetail.DATA_PLANE_BLOCKED -> R.string.server_scan_detail_data_plane_blocked
    ServerProbeDetail.DATA_UNVERIFIED -> R.string.server_scan_detail_data_unverified
    ServerProbeDetail.PROTOCOL_BLOCKED -> R.string.server_scan_detail_protocol_blocked
    ServerProbeDetail.IP_UNREACHABLE -> R.string.server_scan_detail_ip_unreachable
    ServerProbeDetail.NO_RESPONSE -> R.string.server_scan_detail_no_response
    ServerProbeDetail.SERVER_BUSY -> R.string.server_scan_detail_server_busy
    ServerProbeDetail.INVALID_RESPONSE -> R.string.server_scan_detail_invalid_response
    ServerProbeDetail.NO_NETWORK -> R.string.server_scan_detail_no_network
    ServerProbeDetail.DNS_FAILED -> R.string.server_scan_detail_dns_failed
    ServerProbeDetail.NO_PUBLIC_KEY -> R.string.server_scan_detail_no_public_key
    ServerProbeDetail.NETWORK_ERROR -> R.string.server_scan_detail_network_error
}
