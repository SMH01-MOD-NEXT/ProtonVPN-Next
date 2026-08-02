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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.ai.AiApiFormat
import ru.protonmod.next.data.ai.AiProviderConfig
import ru.protonmod.next.ui.screens.settings.SettingRowWithIcon
import ru.protonmod.next.ui.screens.settings.SettingsCategory
import ru.protonmod.next.ui.theme.ProtonNextTheme

/** Provider, model and API key configuration, including user-defined providers. */
@Composable
fun AiProviderSection(
    state: AiSettingsUiState,
    onProviderSelect: (AiProviderConfig) -> Unit,
    onModelSelect: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onSaveCustomProvider: (String?, String, String, AiApiFormat) -> Unit,
    onDeleteCustomProvider: (String) -> Unit,
    onClearFormError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var editedProvider by remember { mutableStateOf<AiProviderConfig?>(null) }
    var showProviderForm by remember { mutableStateOf(false) }
    val colors = ProtonNextTheme.colors

    SettingsCategory(modifier = modifier, title = stringResource(R.string.ai_provider)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.ai_provider),
            subtitle = state.selectedProvider.displayName,
            onClick = { showProviderDialog = true }
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.SettingsSuggest,
            title = stringResource(R.string.ai_model),
            subtitle = state.selectedModel.ifBlank { stringResource(R.string.ai_model_not_selected) },
            onClick = { showModelDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(stringResource(R.string.ai_api_key)) },
            placeholder = { Text(stringResource(R.string.ai_hint_api_key)) },
            singleLine = true,
            colors = aiFieldColors(),
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.providers.forEach { provider ->
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
                                selected = state.selectedProvider.id == provider.id,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.displayName, color = colors.textNorm)
                                if (provider.isCustom) {
                                    Text(
                                        text = provider.baseUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textWeak
                                    )
                                }
                            }
                            if (provider.isCustom) {
                                IconButton(onClick = {
                                    editedProvider = provider
                                    showProviderDialog = false
                                    showProviderForm = true
                                }) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.ai_provider_edit),
                                        tint = colors.textWeak
                                    )
                                }
                                IconButton(onClick = { onDeleteCustomProvider(provider.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.ai_provider_delete),
                                        tint = colors.textWeak
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        editedProvider = null
                        showProviderDialog = false
                        showProviderForm = true
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.brandNorm)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_provider_add), color = colors.brandNorm)
                    }
                }
            },
            confirmButton = {},
            containerColor = colors.backgroundSecondary
        )
    }

    if (showProviderForm) {
        AiProviderFormDialog(
            provider = editedProvider,
            formError = state.formError,
            onDismiss = {
                showProviderForm = false
                onClearFormError()
            },
            onSave = { name, baseUrl, format ->
                onSaveCustomProvider(editedProvider?.id, name, baseUrl, format)
                showProviderForm = false
            }
        )
    }

    if (showModelDialog) {
        AiModelDialog(
            state = state,
            onDismiss = { showModelDialog = false },
            onRefreshModels = onRefreshModels,
            onModelSelect = {
                onModelSelect(it)
                showModelDialog = false
            }
        )
    }
}

@Composable
private fun AiProviderFormDialog(
    provider: AiProviderConfig?,
    formError: AiProviderFormError?,
    onDismiss: () -> Unit,
    onSave: (String, String, AiApiFormat) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    var name by remember(provider) { mutableStateOf(provider?.displayName.orEmpty()) }
    var baseUrl by remember(provider) { mutableStateOf(provider?.baseUrl.orEmpty()) }
    var format by remember(provider) { mutableStateOf(provider?.format ?: AiApiFormat.OPENAI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (provider == null) R.string.ai_provider_add else R.string.ai_provider_edit
                ),
                color = colors.textNorm
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_provider_name)) },
                    singleLine = true,
                    colors = aiFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_provider_base_url)) },
                    placeholder = { Text(stringResource(R.string.ai_provider_base_url_hint)) },
                    singleLine = true,
                    colors = aiFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.ai_provider_base_url_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak
                )
                Text(
                    text = stringResource(R.string.ai_provider_compatibility),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textNorm
                )
                AiApiFormat.userSelectable.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { format = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = format == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                when (option) {
                                    AiApiFormat.ANTHROPIC -> R.string.ai_provider_compat_anthropic
                                    else -> R.string.ai_provider_compat_openai
                                }
                            ),
                            color = colors.textNorm
                        )
                    }
                }
                formError?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                AiProviderFormError.NAME_REQUIRED -> R.string.ai_provider_error_name
                                AiProviderFormError.INVALID_URL -> R.string.ai_provider_error_url
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, baseUrl, format) }) {
                Text(stringResource(R.string.ai_provider_save), color = colors.brandNorm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel), color = colors.textWeak)
            }
        },
        containerColor = colors.backgroundSecondary
    )
}

@Composable
private fun AiModelDialog(
    state: AiSettingsUiState,
    onDismiss: () -> Unit,
    onRefreshModels: () -> Unit,
    onModelSelect: (String) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    var manualModel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_model_select), color = colors.textNorm) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.availableModels.forEach { model ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelect(model) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.selectedModel == model,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(model, color = colors.textNorm)
                    }
                }

                when (state.modelsStatus) {
                    AiModelsStatus.LOADING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.brandNorm)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ai_models_loading), color = colors.textWeak)
                    }
                    AiModelsStatus.NO_API_KEY -> Text(
                        text = stringResource(R.string.ai_models_no_key),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    AiModelsStatus.FAILED -> Text(
                        text = stringResource(R.string.ai_models_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> Unit
                }

                TextButton(onClick = onRefreshModels, enabled = state.modelsStatus != AiModelsStatus.LOADING) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.brandNorm)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_model_refresh), color = colors.brandNorm)
                }

                OutlinedTextField(
                    value = manualModel,
                    onValueChange = { manualModel = it },
                    label = { Text(stringResource(R.string.ai_model_manual)) },
                    singleLine = true,
                    colors = aiFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onModelSelect(manualModel) },
                enabled = manualModel.isNotBlank()
            ) {
                Text(stringResource(R.string.ai_provider_save), color = colors.brandNorm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel), color = colors.textWeak)
            }
        },
        containerColor = colors.backgroundSecondary
    )
}

@Composable
private fun aiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ProtonNextTheme.colors.brandNorm,
    unfocusedBorderColor = ProtonNextTheme.colors.separatorNorm,
    focusedLabelColor = ProtonNextTheme.colors.brandNorm,
    cursorColor = ProtonNextTheme.colors.brandNorm,
    unfocusedLabelColor = ProtonNextTheme.colors.textWeak,
    focusedTextColor = ProtonNextTheme.colors.textNorm,
    unfocusedTextColor = ProtonNextTheme.colors.textNorm
)
