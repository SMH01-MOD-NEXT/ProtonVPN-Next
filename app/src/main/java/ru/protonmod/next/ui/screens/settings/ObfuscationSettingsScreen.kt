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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfuscationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.obfuscation_title),
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_jc),
                    value = uiState.awgJc.toString(),
                    onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(v, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ObfuscationParamField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.obfuscation_jmin),
                        value = uiState.awgJmin.toString(),
                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, v, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                    )
                    ObfuscationParamField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.obfuscation_jmax),
                        value = uiState.awgJmax.toString(),
                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, v, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ObfuscationParamField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.obfuscation_s1),
                        value = uiState.awgS1.toString(),
                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, v, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                    )
                    ObfuscationParamField(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.obfuscation_s2),
                        value = uiState.awgS2.toString(),
                        onValueChange = { val v = it.toIntOrNull() ?: 0; viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, v, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                    )
                }
            }
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_h1),
                    value = uiState.awgH1,
                    isNumeric = false,
                    onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, it, uiState.awgH2, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                )
            }
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_h2),
                    value = uiState.awgH2,
                    isNumeric = false,
                    onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, it, uiState.awgH3, uiState.awgH4, uiState.awgI1) }
                )
            }
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_h3),
                    value = uiState.awgH3,
                    isNumeric = false,
                    onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, it, uiState.awgH4, uiState.awgI1) }
                )
            }
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_h4),
                    value = uiState.awgH4,
                    isNumeric = false,
                    onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, it, uiState.awgI1) }
                )
            }
            item {
                ObfuscationParamField(
                    label = stringResource(R.string.obfuscation_i1),
                    value = uiState.awgI1,
                    isNumeric = false,
                    onValueChange = { viewModel.setAwgParams(uiState.awgJc, uiState.awgJmin, uiState.awgJmax, uiState.awgS1, uiState.awgS2, uiState.awgH1, uiState.awgH2, uiState.awgH3, uiState.awgH4, it) }
                )
            }
        }
    }
}

@Composable
fun ObfuscationParamField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isNumeric: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.textWeak) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textNorm,
            unfocusedTextColor = colors.textNorm,
            focusedBorderColor = colors.brandNorm,
            unfocusedBorderColor = colors.shade20,
            cursorColor = colors.brandNorm
        ),
        singleLine = true
    )
}
