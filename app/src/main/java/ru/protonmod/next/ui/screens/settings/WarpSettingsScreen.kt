/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.local.WarpPreferences
import ru.protonmod.next.data.network.WarpEndpoints
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun WarpSettingsScreen(onBack: () -> Unit, viewModel: WarpSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember(state.preferences) { mutableStateOf(state.preferences) }
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors
    val valid = remember(draft) { runCatching { draft.validate() }.isSuccess }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let {
        Toast.makeText(context, context.getString(it), Toast.LENGTH_LONG).show(); viewModel.clearMessage()
    } }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        colors.brandNorm.copy(alpha = 0.25f), colors.backgroundNorm))).statusBarsPadding().verticalScroll(rememberScrollState())) {
        NavigationHeader(stringResource(R.string.custom_warp), onBack)
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCategory(title = stringResource(R.string.custom_warp)) {
                SettingToggleRow(title = stringResource(R.string.custom_warp), subtitle = stringResource(R.string.warp_enable_desc),
                    icon = ProtonIcons.Cloudflare, checked = draft.enabled, enabled = !state.busy,
                    onCheckedChange = { draft = draft.copy(enabled = it); viewModel.save(draft) })
            }
            SettingsCategory(title = stringResource(R.string.warp_parameters)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WarpOptionsForm(draft, { draft = it }, !state.busy)
                    Text(stringResource(R.string.warp_native_notice), style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
                }
            }
            Text(stringResource(R.string.custom_warp_notice), style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !state.busy, onClick = { viewModel.save(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.custom_save))
                }
                Button(enabled = valid && !state.busy, onClick = { viewModel.generate(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.config == null) R.string.custom_generate else R.string.warp_regenerate))
                }
            }
            if (state.config != null) {
                Text(stringResource(R.string.warp_replace_notice), color = colors.textWeak, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = draft.enabled && state.preferences.enabled && !state.busy, onClick = { viewModel.addHop() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.custom_hop))
                }
                TextButton(enabled = !state.busy, onClick = { confirmDelete = true }) { Text(stringResource(R.string.custom_delete)) }
            }
            Spacer(Modifier.height(140.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.warp_delete_title)) },
        text = { Text(stringResource(R.string.warp_delete_notice)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.custom_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.custom_cancel)) } },
        containerColor = colors.backgroundNorm)
}

@Composable
internal fun WarpOptionsForm(value: WarpPreferences, onChange: (WarpPreferences) -> Unit, enabled: Boolean) {
    val api = stringResource(R.string.warp_api_endpoint)
    val custom = stringResource(R.string.warp_custom_endpoint)
    val ips = remember(api, custom) { listOf("" to api) + WarpEndpoints.allIps.map { it to it } + listOf("custom" to custom) }
    WarpDropdown(stringResource(R.string.warp_endpoint), if (value.customEndpoint) "custom" else value.endpoint,
        ips, enabled) { onChange(value.copy(customEndpoint = it == "custom", endpoint = if (it == "custom") value.endpoint else it)) }
    if (value.customEndpoint) OutlinedTextField(value.customHost, { onChange(value.copy(customHost = it.take(253))) },
        enabled = enabled, singleLine = true, label = { Text(custom) }, modifier = Modifier.fillMaxWidth(),
        isError = value.customHost.isNotEmpty() && runCatching { value.resolvedEndpoint() }.isFailure)
    WarpDropdown(stringResource(R.string.warp_port), value.port.toString(),
        WarpEndpoints.orderedPorts.map { it.toString() to it.toString() }, enabled) { onChange(value.copy(port = it.toInt())) }
    WarpDropdown(stringResource(R.string.warp_profile), value.profile.toString(),
        listOf("0" to "WireGuard", "1" to "WARP #1", "2" to "WARP #2", "3" to "WARP #3"), enabled) { onChange(value.copy(profile = it.toInt())) }
    Text(stringResource(when (value.profile) {
        1 -> R.string.warp_profile_1_desc
        2 -> R.string.warp_profile_2_desc
        3 -> R.string.warp_profile_3_desc
        else -> R.string.warp_profile_wg_desc
    }), style = MaterialTheme.typography.bodySmall, color = ProtonNextTheme.colors.textWeak)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarpDropdown(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(options.firstOrNull { it.first == selected }?.second.orEmpty(), {},
            readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled))
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(key) }) }
        }
    }
}
