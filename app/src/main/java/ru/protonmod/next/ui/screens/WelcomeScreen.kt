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

package ru.protonmod.next.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    // State to trigger the entry animations
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Short delay before starting animations for a smoother UX
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundNorm)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top section with Icon and Texts
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 }
            ) {
                // Placeholder for Proton Next logo. Using a shield icon for now.
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = colors.brandNorm
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 200)) + slideInVertically(tween(800, delayMillis = 200)) { it / 4 }
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 400)) + slideInVertically(tween(800, delayMillis = 400)) { it / 4 }
            ) {
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Bottom section with buttons
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(800, delayMillis = 600)) + slideInVertically(tween(800, delayMillis = 600)) { it / 4 }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateToRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.interactionNorm,
                        contentColor = colors.textInverted
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_create_account),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.textNorm
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(colors.separatorNorm)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_login),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
