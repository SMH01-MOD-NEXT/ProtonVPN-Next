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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * Screen for selecting the VPN Protocol.
 * Currently only displays AmneziaWG, with a shortcut to Obfuscation settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolScreen(
    onBack: () -> Unit,
    onNavigateToObfuscation: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    // In the future, you can get the selected protocol from the viewModel
    // val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.protocol_title),
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
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Protocol Item: AmneziaWG
                        ProtocolItemRow(
                            title = stringResource(R.string.protocol_amneziawg),
                            description = stringResource(R.string.protocol_amneziawg_desc),
                            isSelected = true, // Hardcoded as selected for now
                            onSelect = { /* TODO: Update selected protocol in VM */ },
                            onSettingsClick = onNavigateToObfuscation
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable row component for selecting a protocol
 */
@Composable
fun ProtocolItemRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSettingsClick: (() -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak
            )
        }

        // Optional Settings Button (e.g., for Obfuscation)
        if (onSettingsClick != null) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = colors.brandNorm
                )
            }
        }

        // Selection Indicator
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.brandNorm,
                unselectedColor = colors.shade60
            )
        )
    }
}