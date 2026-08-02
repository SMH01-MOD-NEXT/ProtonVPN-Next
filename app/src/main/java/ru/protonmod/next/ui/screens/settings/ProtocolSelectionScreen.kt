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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolSelectionScreen(
    currentProtocol: String,
    onBack: () -> Unit,
    onProtocolSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val protocols = remember { listOf("AmneziaWG") } // Add more if needed later
    val isTablet = isTablet()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
            ) {
                NavigationHeader(
                    title = stringResource(R.string.title_select_protocol),
                    onBack = onBack
                )

                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                // Header Icon
                Box(
                    modifier = contentModifier.padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(colors.brandNorm.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ProtonIcons.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.title_select_protocol),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(R.string.protocol_amneziawg_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                protocols.forEach { protocol ->
                    val isSelected = protocol == currentProtocol
                    
                    Box(
                        modifier = contentModifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = if (isSelected) 0.6f else 0.4f,
                                shadowElevation = 0.dp
                            )
                            .clickable { onProtocolSelect(protocol) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = protocol,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.brandNorm else colors.textNorm
                                )
                                Text(
                                    text = if (protocol == "AmneziaWG") stringResource(R.string.protocol_amneziawg_desc) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textWeak
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = ProtonIcons.CheckmarkCircle,
                                    contentDescription = null,
                                    tint = colors.brandNorm,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                RadioButton(
                                    selected = false,
                                    onClick = { onProtocolSelect(protocol) },
                                    colors = RadioButtonDefaults.colors(unselectedColor = colors.shade60)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
