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

/** Side action beside the Countries title. No collapsible custom-config section. */
@Composable
fun AddConfigButton(busy: Boolean, onWarp: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !busy) {
            Icon(ru.protonmod.next.ui.icons.ProtonIcons.Plus,
                contentDescription = stringResource(R.string.custom_add_menu), tint = ProtonNextTheme.colors.iconNorm)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.custom_warp)) },
                leadingIcon = { Icon(ru.protonmod.next.ui.icons.ProtonIcons.Cloudflare, null) },
                onClick = { open = false; onWarp() })
            DropdownMenuItem(text = { Text(stringResource(R.string.custom_add)) },
                leadingIcon = { Icon(ru.protonmod.next.ui.icons.ProtonIcons.Plus, null) },
                onClick = { open = false; onImport() })
        }
    }
}

@Composable
internal fun CustomConfigDialog(config: CustomConfig?, onDismiss: () -> Unit,
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
