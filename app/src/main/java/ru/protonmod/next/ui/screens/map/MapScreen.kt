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

package ru.protonmod.next.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.ui.screens.dashboard.DashboardUiState
import ru.protonmod.next.ui.screens.dashboard.DashboardViewModel
import ru.protonmod.next.ui.screens.dashboard.HomeMap
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val successState = uiState as? DashboardUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Network", color = colors.textNorm) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.backgroundNorm)
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            HomeMap(
                modifier = Modifier.fillMaxSize(),
                allServers = successState?.servers ?: emptyList(),
                connectedServer = successState?.connectedServer,
                isConnecting = successState?.isConnecting ?: false,
                isInteractive = true,
                onNodeClick = { countryCode ->
                    viewModel.connectToCountry(countryCode)
                }
            )

            // Интерактивная подсказка поверх карты
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(colors.backgroundSecondary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Pinch to zoom • Tap a node to connect",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textNorm
                )
            }
        }
    }
}