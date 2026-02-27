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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.protonmod.next.ui.screens.dashboard.HomeMap
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map", color = colors.textNorm) },
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
                connectedServer = null,
                isConnecting = false
            )
        }
    }
}
