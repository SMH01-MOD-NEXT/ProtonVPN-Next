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

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfuscationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()

    // Aggregate profiles logic
    val standardProfileName = stringResource(R.string.obfuscation_config_standard)
    val standardProfile = remember(standardProfileName) { ObfuscationProfile.getStandardProfile(standardProfileName) }

    val allProfiles = remember(uiState.customObfuscationProfiles) {
        listOf(standardProfile) + uiState.customObfuscationProfiles
    }

    val selectedProfile = allProfiles.find { it.id == uiState.selectedProfileId } ?: standardProfile

    var showConfigDropdown by remember { mutableStateOf(false) }

    val protectionMode = when {
        uiState.proxyChainEnabled -> ProtectionMode.PROXY_CHAIN
        uiState.isObfuscationEnabled -> ProtectionMode.AWG
        else -> ProtectionMode.OFF
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        DisposableEffect(Unit) {
            onDispose {
                showConfigDropdown = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                item(contentType = "Header") {
                    val headerContentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NavigationHeader(
                            title = stringResource(R.string.obfuscation_title),
                            onBack = onBack,
                            actions = {
                                if (protectionMode == ProtectionMode.AWG) {
                                    IconButton(onClick = { viewModel.resetToStandard() }) {
                                        Icon(
                                            imageVector = ProtonIcons.ArrowsRotate,
                                            contentDescription = stringResource(R.string.settings_reset_obfuscation),
                                            tint = colors.brandNorm
                                        )
                                    }
                                }
                            }
                        )
                        Box(
                            modifier = headerContentModifier.padding(top = 24.dp, bottom = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .background(colors.brandNorm.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ProtonIcons.EyeSlash,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = colors.brandNorm
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.obfuscation_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            textAlign = TextAlign.Center,
                            modifier = headerContentModifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.obfuscation_enable_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = headerContentModifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                item(contentType = "ProtectionMode") {
                    Column(
                        modifier = contentModifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryHeader(title = stringResource(R.string.obfuscation_protection_mode))
                        ProtectionModeSelector(
                            selectedMode = protectionMode,
                            onSelectMode = { mode ->
                                when (mode) {
                                    ProtectionMode.OFF -> viewModel.setConnectionProtectionMode(
                                        proxyChainEnabled = false,
                                        obfuscationEnabled = false
                                    )
                                    ProtectionMode.AWG -> viewModel.setConnectionProtectionMode(
                                        proxyChainEnabled = false,
                                        obfuscationEnabled = true
                                    )
                                    ProtectionMode.PROXY_CHAIN -> viewModel.setConnectionProtectionMode(
                                        proxyChainEnabled = true,
                                        obfuscationEnabled = false
                                    )
                                }
                            }
                        )
                    }
                }

                item(contentType = "DisabledHint") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.OFF,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        InfoCard(text = stringResource(R.string.obfuscation_off_info))
                    }
                }

                item(contentType = "ProxyChainSettings") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.PROXY_CHAIN,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryHeader(title = stringResource(R.string.proxy_chain_setup_title))
                            InfoCard(text = stringResource(R.string.proxy_chain_info))
                            ProxyChainEditor(
                                config = uiState.proxyChainConfig,
                                onConfigChange = viewModel::setProxyChainConfig,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Deconstructed detailed settings for better lazy loading and memory management
                item(contentType = "AWG_Info") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        InfoCard(text = stringResource(R.string.obfuscation_info_desc))
                    }
                }

                item(contentType = "AWG_ConfigSelector") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
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
                        }
                    }
                }

                item(contentType = "AWG_TuningMode") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_tuning_mode))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !uiState.isObfuscationAdvancedMode,
                                    onClick = { viewModel.setObfuscationAdvancedMode(false) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = colors.brandNorm,
                                        activeContentColor = colors.onInteraction,
                                        inactiveContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                        inactiveContentColor = colors.textNorm
                                    )
                                ) {
                                    Text(stringResource(R.string.obfuscation_mode_easy))
                                }
                                SegmentedButton(
                                    selected = uiState.isObfuscationAdvancedMode,
                                    onClick = { viewModel.setObfuscationAdvancedMode(true) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = colors.brandNorm,
                                        activeContentColor = colors.onInteraction,
                                        inactiveContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                        inactiveContentColor = colors.textNorm
                                    )
                                ) {
                                    Text(stringResource(R.string.obfuscation_mode_advanced))
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_EasyMode") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && !uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_junk_level))
                            SettingsCard {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    val presets = listOf(
                                        stringResource(R.string.obfuscation_junk_low),
                                        stringResource(R.string.obfuscation_junk_medium),
                                        stringResource(R.string.obfuscation_junk_high)
                                    )
                                    presets.forEachIndexed { index, label ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !selectedProfile.isReadOnly) { viewModel.applyJunkPreset(index) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = uiState.awgJunkLevel == index,
                                                onClick = { viewModel.applyJunkPreset(index) },
                                                enabled = !selectedProfile.isReadOnly,
                                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(label, color = colors.textNorm)
                                        }
                                    }
                                    if (uiState.awgJunkLevel == 3) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = true,
                                                onClick = null,
                                                enabled = false,
                                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(stringResource(R.string.obfuscation_junk_custom), color = colors.textNorm)
                                        }
                                    }
                                }
                            }

                            CategoryHeader(title = stringResource(R.string.obfuscation_category_advanced))
                            SettingsCard {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.obfuscation_i1_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textWeak
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.backgroundNorm)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            uiState.awgI1,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textWeak,
                                            maxLines = 3
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.randomizeI1() },
                                        enabled = !selectedProfile.isReadOnly,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                                    ) {
                                        Icon(ProtonIcons.ArrowsRotate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.obfuscation_regenerate_i1))
                                    }

                                    var showDomainDialog by remember { mutableStateOf(false) }
                                    OutlinedButton(
                                        onClick = { showDomainDialog = true },
                                        enabled = !selectedProfile.isReadOnly,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colors.brandNorm.copy(alpha = 0.5f))
                                    ) {
                                        Icon(ProtonIcons.Globe, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.brandNorm)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.obfuscation_btn_generate_from_domain), color = colors.brandNorm)
                                    }

                                    if (showDomainDialog) {
                                        var domainInput by remember { mutableStateOf("") }
                                        AlertDialog(
                                            onDismissRequest = { showDomainDialog = false },
                                            title = { Text(stringResource(R.string.obfuscation_dialog_domain_title), color = colors.textNorm) },
                                            text = {
                                                Column {
                                                    Text(stringResource(R.string.obfuscation_dialog_domain_desc), style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    SmoothOutlinedTextField(
                                                        value = domainInput,
                                                        onValueChange = { domainInput = it },
                                                        placeholder = { Text("google.com", color = colors.textWeak.copy(alpha = 0.5f)) },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = colors.brandNorm,
                                                            focusedTextColor = colors.textNorm,
                                                            unfocusedTextColor = colors.textNorm
                                                        )
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(
                                                    onClick = {
                                                        if (domainInput.isNotBlank()) {
                                                            viewModel.generateI1FromDomain(domainInput.trim())
                                                        }
                                                        showDomainDialog = false
                                                    }
                                                ) {
                                                    Text(stringResource(android.R.string.ok), color = colors.brandNorm)
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDomainDialog = false }) {
                                                    Text(stringResource(android.R.string.cancel), color = colors.textWeak)
                                                }
                                            },
                                            containerColor = colors.backgroundSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedJunk") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_junk))
                            SettingsCard {
                                ObfuscationParamField(
                                    label = stringResource(R.string.obfuscation_jc),
                                    value = uiState.awgJc.toString(),
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("jc", v) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.shade20.copy(alpha = 0.5f))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_jmin),
                                        value = uiState.awgJmin.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("jmin", v) }
                                    )
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_jmax),
                                        value = uiState.awgJmax.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("jmax", v) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedMagic") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_magic))
                            SettingsCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_s1),
                                        value = uiState.awgS1.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("s1", v) }
                                    )
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_s2),
                                        value = uiState.awgS2.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("s2", v) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_s3),
                                        value = uiState.awgS3.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("s3", v) }
                                    )
                                    ObfuscationParamField(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.obfuscation_s4),
                                        value = uiState.awgS4.toString(),
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.updateAwgParam("s4", v) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedHeaders") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_headers))
                            SettingsCard {
                                ObfuscationParamField(
                                    label = stringResource(R.string.obfuscation_h1),
                                    value = uiState.awgH1,
                                    isNumeric = false,
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { viewModel.updateAwgParam("h1", it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ObfuscationParamField(
                                    label = stringResource(R.string.obfuscation_h2),
                                    value = uiState.awgH2,
                                    isNumeric = false,
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { viewModel.updateAwgParam("h2", it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ObfuscationParamField(
                                    label = stringResource(R.string.obfuscation_h3),
                                    value = uiState.awgH3,
                                    isNumeric = false,
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { viewModel.updateAwgParam("h3", it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ObfuscationParamField(
                                    label = stringResource(R.string.obfuscation_h4),
                                    value = uiState.awgH4,
                                    isNumeric = false,
                                    isEnabled = !selectedProfile.isReadOnly,
                                    onValueChange = { viewModel.updateAwgParam("h4", it) }
                                )
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedStrings") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_advanced))
                            SettingsCard {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_i1),
                                        value = uiState.awgI1,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("i1", it) }
                                    )
                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_i2),
                                        value = uiState.awgI2,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("i2", it) }
                                    )
                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_i3),
                                        value = uiState.awgI3,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("i3", it) }
                                    )
                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_i4),
                                        value = uiState.awgI4,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("i4", it) }
                                    )
                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_i5),
                                        value = uiState.awgI5,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("i5", it) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedMimicry") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_mimicry))
                            SettingsCard {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (uiState.awgHeaderProtectionKey.isNotBlank() && (uiState.awgS1 < 12 || uiState.awgS2 < 12 || uiState.awgS3 < 12 || uiState.awgS4 < 12)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(ProtonIcons.ExclamationTriangleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.obfuscation_hp_warning), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_hp_key),
                                            value = uiState.awgHeaderProtectionKey,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("hp_key", it) }
                                        )
                                        IconButton(
                                            onClick = { viewModel.generateHeaderProtectionKey() },
                                            enabled = !selectedProfile.isReadOnly,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(colors.backgroundNorm, RoundedCornerShape(12.dp))
                                        ) {
                                            Icon(ProtonIcons.MagicProtonWand, contentDescription = stringResource(R.string.obfuscation_hp_generate), tint = colors.brandNorm)
                                        }
                                    }
                                    Text(stringResource(R.string.obfuscation_hp_key_desc), style = MaterialTheme.typography.labelSmall, color = colors.textWeak)

                                    HorizontalDivider(color = colors.shade20.copy(alpha = 0.5f))

                                    ObfuscationParamField(
                                        label = stringResource(R.string.obfuscation_cp_addition),
                                        value = uiState.awgContentPaddingAddition,
                                        isNumeric = false,
                                        isEnabled = !selectedProfile.isReadOnly,
                                        onValueChange = { viewModel.updateAwgParam("cp_addition", it) }
                                    )
                                    Text(stringResource(R.string.obfuscation_cp_addition_desc), style = MaterialTheme.typography.labelSmall, color = colors.textWeak)
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_AdvancedTimings") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG && uiState.isObfuscationAdvancedMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CategoryHeader(title = stringResource(R.string.obfuscation_category_timings))
                            SettingsCard {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.obfuscation_range_hint), style = MaterialTheme.typography.labelSmall, color = colors.brandNorm.copy(alpha = 0.7f))
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_rekey_after),
                                            value = uiState.awgRekeyAfterTime,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("rekey_after", it) }
                                        )
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_rekey_timeout),
                                            value = uiState.awgRekeyTimeout,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("rekey_timeout", it) }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_reject_after),
                                            value = uiState.awgRejectAfterTime,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("reject_after", it) }
                                        )
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_keepalive_timeout),
                                            value = uiState.awgKeepaliveTimeout,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("keepalive_timeout", it) }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_max_handshake),
                                            value = uiState.awgMaxHandshakeAttempts,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("max_handshake", it) }
                                        )
                                        ObfuscationParamField(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.obfuscation_persistent_keepalive),
                                            value = uiState.awgPersistentKeepalive,
                                            isNumeric = false,
                                            isEnabled = !selectedProfile.isReadOnly,
                                            onValueChange = { viewModel.updateAwgParam("persistent_keepalive", it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(contentType = "AWG_Actions") {
                    AnimatedVisibility(
                        visible = protectionMode == ProtectionMode.AWG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Button to explicitly update current custom config if it's not read-only
                            if (!selectedProfile.isReadOnly) {
                                Button(
                                    onClick = {
                                        val updatedProfile = selectedProfile.copy(
                                            jc = uiState.awgJc, jmin = uiState.awgJmin, jmax = uiState.awgJmax,
                                            s1 = uiState.awgS1, s2 = uiState.awgS2, s3 = uiState.awgS3, s4 = uiState.awgS4,
                                            h1 = uiState.awgH1, h2 = uiState.awgH2, h3 = uiState.awgH3, h4 = uiState.awgH4,
                                            i1 = uiState.awgI1, i2 = uiState.awgI2, i3 = uiState.awgI3, i4 = uiState.awgI4, i5 = uiState.awgI5,
                                            headerProtectionKey = uiState.awgHeaderProtectionKey,
                                            contentPaddingAddition = uiState.awgContentPaddingAddition,
                                            rekeyAfterTime = uiState.awgRekeyAfterTime,
                                            rekeyTimeout = uiState.awgRekeyTimeout,
                                            rejectAfterTime = uiState.awgRejectAfterTime,
                                            keepaliveTimeout = uiState.awgKeepaliveTimeout,
                                            maxHandshakeAttempts = uiState.awgMaxHandshakeAttempts,
                                            persistentKeepaliveInterval = uiState.awgPersistentKeepalive,
                                            junkLevel = uiState.awgJunkLevel
                                        )
                                        viewModel.saveObfuscationProfile(updatedProfile)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.backgroundSecondary)
                                ) {
                                    Icon(ProtonIcons.ArrowDownToSquare, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textNorm)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_save), color = colors.textNorm)
                                }
                            }

                            // Create New Profile Button
                            var showSaveDialog by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(ProtonIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.brandNorm)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.obfuscation_save_config), color = colors.brandNorm)
                            }

                            if (showSaveDialog) {
                                var newProfileName by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showSaveDialog = false },
                                    title = { Text(stringResource(R.string.obfuscation_save_config), color = colors.textNorm) },
                                    text = {
                                        SmoothOutlinedTextField(
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
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        name = newProfileName,
                                                        isReadOnly = false,
                                                        jc = uiState.awgJc, jmin = uiState.awgJmin, jmax = uiState.awgJmax,
                                                        s1 = uiState.awgS1, s2 = uiState.awgS2, s3 = uiState.awgS3, s4 = uiState.awgS4,
                                                        h1 = uiState.awgH1, h2 = uiState.awgH2, h3 = uiState.awgH3, h4 = uiState.awgH4,
                                                        i1 = uiState.awgI1, i2 = uiState.awgI2, i3 = uiState.awgI3, i4 = uiState.awgI4, i5 = uiState.awgI5,
                                                        headerProtectionKey = uiState.awgHeaderProtectionKey,
                                                        contentPaddingAddition = uiState.awgContentPaddingAddition,
                                                        rekeyAfterTime = uiState.awgRekeyAfterTime,
                                                        rekeyTimeout = uiState.awgRekeyTimeout,
                                                        rejectAfterTime = uiState.awgRejectAfterTime,
                                                        keepaliveTimeout = uiState.awgKeepaliveTimeout,
                                                        maxHandshakeAttempts = uiState.awgMaxHandshakeAttempts,
                                                        persistentKeepaliveInterval = uiState.awgPersistentKeepalive,
                                                        junkLevel = uiState.awgJunkLevel
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

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

private enum class ProtectionMode {
    OFF,
    AWG,
    PROXY_CHAIN
}

@Composable
private fun ProtectionModeSelector(
    selectedMode: ProtectionMode,
    onSelectMode: (ProtectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(modifier = modifier) {
        ProtectionModeOption(
            title = stringResource(R.string.obfuscation_mode_off),
            description = stringResource(R.string.obfuscation_mode_off_desc),
            selected = selectedMode == ProtectionMode.OFF
        ) { onSelectMode(ProtectionMode.OFF) }
        HorizontalDivider(color = ProtonNextTheme.colors.shade20.copy(alpha = 0.5f))
        ProtectionModeOption(
            title = stringResource(R.string.obfuscation_transport_awg),
            description = stringResource(R.string.obfuscation_mode_awg_desc),
            selected = selectedMode == ProtectionMode.AWG,
            onClick = { onSelectMode(ProtectionMode.AWG) }
        )
        HorizontalDivider(color = ProtonNextTheme.colors.shade20.copy(alpha = 0.5f))
        ProtectionModeOption(
            title = stringResource(R.string.obfuscation_transport_proxy_chain),
            description = stringResource(R.string.obfuscation_mode_proxy_desc),
            selected = selectedMode == ProtectionMode.PROXY_CHAIN,
            onClick = { onSelectMode(ProtectionMode.PROXY_CHAIN) }
        )
    }
}

@Composable
private fun ProtectionModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.brandNorm.copy(alpha = 0.10f) else colors.backgroundNorm.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.brandNorm,
                unselectedColor = colors.shade60
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) colors.brandNorm else colors.textNorm
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak
            )
        }
    }
}

@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.3f, shadowElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ProtonIcons.InfoCircle,
                contentDescription = null,
                tint = colors.brandNorm,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textNorm
            )
        }
    }
}

@Composable
fun CategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = ProtonNextTheme.colors.brandNorm,
        modifier = modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun ObfuscationParamField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isNumeric: Boolean = true
) {
    val colors = ProtonNextTheme.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textWeak
        )
        Spacer(modifier = Modifier.height(4.dp))
        SmoothOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = isEnabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandNorm,
                unfocusedBorderColor = colors.shade20,
                disabledBorderColor = colors.shade10,
                focusedTextColor = colors.textNorm,
                unfocusedTextColor = colors.textNorm,
                disabledTextColor = colors.textWeak
            )
        )
    }
}
