/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.countries

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.R
import ru.protonmod.next.data.repository.CustomConfig
import ru.protonmod.next.vpn.ImportedWireGuard
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun CustomConfigsPanel(configs: List<CustomConfig>, busy: Boolean,
    onSave: (String?, String, String, String, String) -> Unit,
    onDelete: (String) -> Unit, onGenerate: () -> Unit,
    onHop: (CustomConfig) -> Unit, onConnect: (CustomConfig) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CustomConfig?>(null) }
    var confirmWarp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.custom_title) + " (${configs.size})") }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = null; editing = true }, enabled = !busy) { Text(stringResource(R.string.custom_add)) }
                OutlinedButton(onClick = { confirmWarp = true }, enabled = !busy) { Text(stringResource(R.string.custom_warp)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                configs.forEach { config ->
                    CountryCard(CountryDisplayItem(config.country, 0),
                        onClick = { onConnect(config) }, onMoreClick = { selected = config; editing = true },
                        customTitle = listOf(config.name, config.country, config.city).filter(String::isNotBlank).joinToString(" · "), showLoad = false)
                }
            }
        }
    }
    if (confirmWarp) AlertDialog(onDismissRequest = { confirmWarp = false },
        title = { Text(stringResource(R.string.custom_warp)) },
        text = { Text(stringResource(R.string.custom_warp_notice)) },
        confirmButton = { TextButton(onClick = { confirmWarp = false; onGenerate() }) { Text(stringResource(R.string.custom_generate)) } },
        dismissButton = { TextButton(onClick = { confirmWarp = false }) { Text(stringResource(R.string.custom_cancel)) } },
        containerColor = ProtonNextTheme.colors.backgroundNorm)
    if (editing) CustomConfigDialog(selected, onDismiss = { editing = false },
        onSave = { name, raw, country, city -> onSave(selected?.id, name, raw, country, city); editing = false },
        onDelete = { selected?.let { onDelete(it.id) }; editing = false },
        onHop = { selected?.let(onHop); editing = false })
}

@Composable
private fun CustomConfigDialog(config: CustomConfig?, onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit, onDelete: () -> Unit, onHop: () -> Unit) {
    var name by remember { mutableStateOf(config?.name.orEmpty()) }
    var raw by remember { mutableStateOf(config?.link?.let { if (it.startsWith(ImportedWireGuard.SCHEME)) ImportedWireGuard.decode(it) else it }.orEmpty()) }
    var country by remember { mutableStateOf(config?.country.orEmpty()) }
    var city by remember { mutableStateOf(config?.city.orEmpty()) }
    var fileError by remember { mutableStateOf(false) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                raw = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use {
                    val buffer = ByteArray(65537)
                    var count = 0
                    while (count < buffer.size) {
                        val read = it.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    require(count <= 65536)
                    buffer.decodeToString(endIndex = count)
                } }
                fileError = false
            } catch (_: Exception) { fileError = true }
        }
    }
    val valid = remember(raw, name, country) { name.isNotBlank() && name.length <= 100 &&
        (country.isBlank() || country.uppercase(java.util.Locale.ROOT) in java.util.Locale.getISOCountries()) &&
        runCatching { if (raw.trimStart().startsWith("[Interface]")) ImportedWireGuard.encode(raw.trim()) else {
            require('\n' !in raw.trim()); require(ru.protonmod.next.vpn.ProxyLinkParser.isValid(raw))
        } }.isSuccess }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.custom_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.custom_hint))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.custom_name)) }, singleLine = true)
            OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.custom_file)) }
            OutlinedTextField(raw, { raw = it }, label = { Text(stringResource(R.string.custom_config_content)) }, minLines = 3, maxLines = 6,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            if (fileError || raw.isNotBlank() && !valid) Text(stringResource(R.string.custom_error), color = ProtonNextTheme.colors.notificationError)
            OutlinedTextField(country, { country = it.take(2) }, label = { Text(stringResource(R.string.custom_country)) }, singleLine = true)
            OutlinedTextField(city, { city = it.take(100) }, label = { Text(stringResource(R.string.custom_city)) }, singleLine = true)
            if (config != null) {
                TextButton(onClick = onHop) { Text(stringResource(R.string.custom_hop)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.custom_delete)) }
            }
        } },
        confirmButton = { TextButton(onClick = { onSave(name, raw, country, city) }, enabled = valid) { Text(stringResource(R.string.custom_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.custom_cancel)) } },
        containerColor = ProtonNextTheme.colors.backgroundNorm)
}
