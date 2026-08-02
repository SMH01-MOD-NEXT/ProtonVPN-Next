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

package ru.protonmod.next.ui.screens.netshield

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.netshield.NetShieldCategory
import ru.protonmod.next.netshield.NetShieldSourcePreset
import ru.protonmod.next.netshield.NetShieldSources
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

/** The user's own blocklist: paste rules, import them from a file or from a URL. */
@Composable
fun NetShieldCustomFiltersSection(
    state: NetShieldSettingsUiState,
    onImportText: (String) -> Unit,
    onImportUrl: (String) -> Unit,
    onPickFile: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    var rules by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    NetShieldCard(modifier = modifier, title = stringResource(R.string.netshield_custom_filters_title)) {
        Text(
            text = stringResource(R.string.netshield_custom_filters_desc),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textWeak,
        )
        Text(
            text = stringResource(R.string.netshield_custom_filters_count, state.lists.customDomainCount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textNorm,
        )

        OutlinedTextField(
            value = rules,
            onValueChange = { rules = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text(stringResource(R.string.netshield_custom_filters_hint)) },
            colors = netShieldFieldColors(),
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    onImportText(rules)
                    rules = ""
                },
                enabled = rules.isNotBlank() && !state.lists.isImporting,
            ) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = colors.brandNorm)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.netshield_custom_filters_add), color = colors.brandNorm)
            }
            TextButton(onClick = onPickFile, enabled = !state.lists.isImporting) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = colors.brandNorm)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.netshield_custom_filters_import_file), color = colors.brandNorm)
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.netshield_source_url_hint)) },
            colors = netShieldFieldColors(),
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    onImportUrl(url)
                    url = ""
                },
                enabled = url.isNotBlank() && !state.lists.isImporting,
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.brandNorm)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.netshield_custom_filters_import_url), color = colors.brandNorm)
            }
            TextButton(onClick = onClear, enabled = state.lists.customDomainCount > 0) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.notificationError)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.netshield_custom_filters_clear), color = colors.notificationError)
            }
        }

        state.lists.importedCount?.let { imported ->
            Text(
                text = stringResource(R.string.netshield_custom_filters_imported, imported),
                style = MaterialTheme.typography.bodySmall,
                color = colors.brandNorm,
            )
        }
    }
}

/** Lets the user replace each default blocklist provider with a preset or their own URL. */
@Composable
fun NetShieldSourcesSection(
    state: NetShieldSettingsUiState,
    onPresetSelected: (NetShieldCategory, String) -> Unit,
    onCustomUrl: (NetShieldCategory, String) -> Unit,
    onResetCategory: (NetShieldCategory) -> Unit,
    onApplyToAll: (String) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    var editedCategory by remember { mutableStateOf<NetShieldCategory?>(null) }
    var showApplyToAll by remember { mutableStateOf(false) }

    NetShieldCard(modifier = modifier, title = stringResource(R.string.netshield_sources_title)) {
        Text(
            text = stringResource(R.string.netshield_sources_desc),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textWeak,
        )

        NetShieldSources.downloadableCategories.forEach { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editedCategory = category }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(categoryTitle(category)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textNorm,
                    )
                    Text(
                        text = sourceLabel(state, category),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showApplyToAll = true }) {
                Text(stringResource(R.string.netshield_source_apply_all), color = colors.brandNorm)
            }
            TextButton(onClick = onResetAll) {
                Text(stringResource(R.string.netshield_source_reset_all), color = colors.textWeak)
            }
        }
    }

    editedCategory?.let { category ->
        CategorySourceDialog(
            category = category,
            state = state,
            onDismiss = { editedCategory = null },
            onPresetSelected = { presetId ->
                onPresetSelected(category, presetId)
                editedCategory = null
            },
            onCustomUrl = { value ->
                onCustomUrl(category, value)
                editedCategory = null
            },
            onReset = {
                onResetCategory(category)
                editedCategory = null
            },
        )
    }

    if (showApplyToAll) {
        PresetPickerDialog(
            title = stringResource(R.string.netshield_source_apply_all),
            presets = NetShieldSources.universalPresets(),
            selectedId = null,
            onDismiss = { showApplyToAll = false },
            onSelected = {
                onApplyToAll(it)
                showApplyToAll = false
            },
        )
    }
}

@Composable
private fun CategorySourceDialog(
    category: NetShieldCategory,
    state: NetShieldSettingsUiState,
    onDismiss: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onCustomUrl: (String) -> Unit,
    onReset: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    var url by remember(category) { mutableStateOf(state.sources.customUrls[category].orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(categoryTitle(category)), color = colors.textNorm) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NetShieldSources.presetsFor(category).forEach { preset ->
                    PresetRow(
                        preset = preset,
                        selected = state.sources.presetIds[category] == preset.id,
                        onClick = { onPresetSelected(preset.id) },
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.netshield_source_custom_url)) },
                    placeholder = { Text(stringResource(R.string.netshield_source_url_hint)) },
                    colors = netShieldFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.netshield_source_reset), color = colors.textWeak)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCustomUrl(url) }, enabled = NetShieldSources.isValidUrl(url)) {
                Text(stringResource(R.string.netshield_save), color = colors.brandNorm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.netshield_cancel), color = colors.textWeak)
            }
        },
        containerColor = colors.backgroundSecondary,
    )
}

@Composable
private fun PresetPickerDialog(
    title: String,
    presets: List<NetShieldSourcePreset>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colors.textNorm) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                presets.forEach { preset ->
                    PresetRow(preset = preset, selected = preset.id == selectedId, onClick = { onSelected(preset.id) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.netshield_cancel), color = colors.textWeak)
            }
        },
        containerColor = colors.backgroundSecondary,
    )
}

@Composable
private fun PresetRow(preset: NetShieldSourcePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.displayName, color = colors.textNorm)
            Text(
                text = preset.url,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetShieldCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textWeak,
            modifier = Modifier.padding(start = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun sourceLabel(state: NetShieldSettingsUiState, category: NetShieldCategory): String = when {
    state.isDefaultSource(category) -> stringResource(R.string.netshield_source_default)
    else -> NetShieldSources.preset(state.sources.presetIds[category])?.displayName ?: state.sourceUrl(category)
}

private fun categoryTitle(category: NetShieldCategory): Int = when (category) {
    NetShieldCategory.MALWARE -> R.string.netshield_category_malware
    NetShieldCategory.ADS -> R.string.netshield_category_ads
    NetShieldCategory.TRACKERS -> R.string.netshield_category_trackers
    NetShieldCategory.ADULT -> R.string.netshield_category_adult
    NetShieldCategory.CUSTOM -> R.string.netshield_custom_filters_title
}

@Composable
private fun netShieldFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ProtonNextTheme.colors.brandNorm,
    unfocusedBorderColor = ProtonNextTheme.colors.separatorNorm,
    focusedLabelColor = ProtonNextTheme.colors.brandNorm,
    cursorColor = ProtonNextTheme.colors.brandNorm,
    unfocusedLabelColor = ProtonNextTheme.colors.textWeak,
    focusedTextColor = ProtonNextTheme.colors.textNorm,
    unfocusedTextColor = ProtonNextTheme.colors.textNorm,
)
