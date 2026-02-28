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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfuscationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    // Aggregate profiles logic
    val standardProfileName = stringResource(R.string.obfuscation_config_standard)
    val standardProfile = remember(standardProfileName) { ObfuscationProfile.getStandardProfile(standardProfileName) }

    val allProfiles = remember(uiState.customObfuscationProfiles) {
        listOf(standardProfile) + uiState.customObfuscationProfiles
    }

    val selectedProfile = allProfiles.find { it.id == uiState.selectedProfileId } ?: standardProfile

    var showConfigDropdown by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.obfuscation_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToStandard() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.settings_reset_obfuscation),
                            tint = colors.brandNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = colors.backgroundNorm
                )
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Toggle
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isObfuscationEnabled) colors.brandNorm.copy(alpha = 0.15f)
                        else colors.backgroundSecondary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setObfuscationEnabled(!uiState.isObfuscationEnabled) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.obfuscation_enable),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textNorm
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.obfuscation_enable_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textWeak
                            )
                        }
                        Switch(
                            checked = uiState.isObfuscationEnabled,
                            onCheckedChange = { viewModel.setObfuscationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.textInverted,
                                checkedTrackColor = colors.brandNorm,
                                uncheckedThumbColor = colors.shade60,
                                uncheckedTrackColor = colors.shade20,
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            // Animated content for detailed settings
            item {
                AnimatedVisibility(
                    visible = uiState.isObfuscationEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        InfoCard(text = stringResource(R.string.obfuscation_info_desc))

                        // Configuration Selector
                        CategoryHeader(title = stringResource(R.string.obfuscation_config))
                        ExposedDropdownMenuBox(
                            expanded = showConfigDropdown,
                            onExpandedChange = { showConfigDropdown = it },
                        ) {
                            OutlinedTextField(
                                value = selectedProfile.name,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showConfigDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.brandNorm,
                                    unfocusedBorderColor = colors.shade20,
                                    focusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                    unfocusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = showConfigDropdown,
                                onDismissRequest = { showConfigDropdown = false },
                                modifier = Modifier.background(colors.backgroundSecondary)
                            ) {
                                allProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name, color = colors.textNorm) },
                                        onClick = {
                                            viewModel.selectObfuscationProfile(profile)
                                            showConfigDropdown = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        // Button to explicitly update current custom config if it's not read-only
                        if (!selectedProfile.isReadOnly) {
                            Button(
                                onClick = {
                                    val updatedProfile = selectedProfile.copy(
                                        jc = uiState.awgJc, jmin = uiState.awgJmin, jmax = uiState.awgJmax,
                                        s1 = uiState.awgS1, s2 = uiState.awgS2,
                                        h1 = uiState.awgH1, h2 = uiState.awgH2, h3 = uiState.awgH3, h4 = uiState.awgH4,
                                        i1 = uiState.awgI1
                                    )
                                    viewModel.saveObfuscationProfile(updatedProfile)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.backgroundSecondary)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textNorm)
                                Spacer(modifier = Modifier.width(8.dp))
                                // Can be moved to strings.xml later: "Update Current Profile"
                                Text("Update Current Config", color = colors.textNorm)
                            }
                        }

                        // Create New Profile Button
                        TextButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.brandNorm)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.obfuscation_save_config), color = colors.brandNorm)
                        }

                        // Parameters (Junk)
                        CategoryHeader(title = stringResource(R.string.obfuscation_category_junk))
                        SettingsCard {
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_jc),
                                value = uiState.awgJc.toString(),
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(v, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.shade20.copy(alpha = 0.5f))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ObfuscationParamField(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.obfuscation_jmin),
                                    value = uiState.awgJmin.toString(),
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, v, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                                )
                                ObfuscationParamField(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.obfuscation_jmax),
                                    value = uiState.awgJmax.toString(),
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, v, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                                )
                            }
                        }

                        // Parameters (Magic)
                        CategoryHeader(title = stringResource(R.string.obfuscation_category_magic))
                        SettingsCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ObfuscationParamField(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.obfuscation_s1),
                                    value = uiState.awgS1.toString(),
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, v, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                                )
                                ObfuscationParamField(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.obfuscation_s2),
                                    value = uiState.awgS2.toString(),
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, v, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                                )
                            }
                        }

                        // Parameters (Headers)
                        CategoryHeader(title = stringResource(R.string.obfuscation_category_headers))
                        SettingsCard {
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_h1),
                                value = uiState.awgH1,
                                isNumeric = false,
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, it, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_h2),
                                value = uiState.awgH2,
                                isNumeric = false,
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, it, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_h3),
                                value = uiState.awgH3,
                                isNumeric = false,
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, it, uiState.awgH4, uiState.awgI1) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_h4),
                                value = uiState.awgH4,
                                isNumeric = false,
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, it, uiState.awgI1) }
                            )
                        }

                        // Parameters (Advanced)
                        CategoryHeader(title = stringResource(R.string.obfuscation_category_advanced))
                        SettingsCard {
                            ObfuscationParamField(
                                label = stringResource(R.string.obfuscation_i1),
                                value = uiState.awgI1,
                                isNumeric = false,
                                isEnabled = !selectedProfile.isReadOnly,
                                onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Save Dialog Integration
    if (showSaveDialog) {
        var newProfileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.obfuscation_save_config), color = colors.textNorm) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(R.string.obfuscation_config_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brandNorm,
                        focusedTextColor = colors.textNorm,
                        unfocusedTextColor = colors.textNorm
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            val newProfile = ObfuscationProfile(
                                id = UUID.randomUUID().toString(),
                                name = newProfileName,
                                isReadOnly = false,
                                jc = uiState.awgJc, jmin = uiState.awgJmin, jmax = uiState.awgJmax,
                                s1 = uiState.awgS1, s2 = uiState.awgS2,
                                h1 = uiState.awgH1, h2 = uiState.awgH2, h3 = uiState.awgH3, h4 = uiState.awgH4,
                                i1 = uiState.awgI1
                            )
                            viewModel.saveObfuscationProfile(newProfile)
                        }
                        showSaveDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok), color = colors.brandNorm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel), color = colors.textWeak)
                }
            },
            containerColor = colors.backgroundSecondary
        )
    }
}

@Composable
fun InfoCard(text: String) {
    val colors = ProtonNextTheme.colors
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.brandNorm.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.brandNorm,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textNorm
            )
        }
    }
}

@Composable
fun CategoryHeader(title: String) {
    val colors = ProtonNextTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = colors.brandNorm,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = ProtonNextTheme.colors
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun ObfuscationParamField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isNumeric: Boolean = true,
    isEnabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = isEnabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = colors.textNorm,
            unfocusedTextColor = colors.textNorm,
            disabledTextColor = colors.textWeak,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = colors.brandNorm,
            unfocusedIndicatorColor = colors.shade20,
            disabledIndicatorColor = colors.shade20.copy(alpha = 0.3f),
            cursorColor = colors.brandNorm,
            focusedLabelColor = colors.brandNorm,
            unfocusedLabelColor = colors.textWeak,
            disabledLabelColor = colors.textWeak.copy(alpha = 0.5f)
        ),
        singleLine = true
    )
}