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

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySpoofingScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()
    val context = LocalContext.current

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
                    title = stringResource(R.string.settings_country_spoofing_title),
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
                            imageVector = ProtonIcons.Globe,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title & Description
                Text(
                    text = stringResource(R.string.settings_country_spoofing_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.settings_country_spoofing_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Settings Card
                Box(
                    modifier = contentModifier
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Master Toggle
                        SettingToggleRow(
                            title = stringResource(R.string.settings_country_spoofing_title),
                            subtitle = if (uiState.spoofCountryEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
                            icon = ProtonIcons.Language,
                            checked = uiState.spoofCountryEnabled,
                            onCheckedChange = { viewModel.setSpoofCountryEnabled(it) }
                        )

                        AnimatedVisibility(
                            visible = uiState.spoofCountryEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                // Null Spoof Toggle
                                SettingToggleRow(
                                    title = stringResource(R.string.settings_country_spoofing_null_label),
                                    subtitle = stringResource(R.string.settings_country_spoofing_null_desc),
                                    icon = Icons.Rounded.Flag,
                                    checked = uiState.spoofCountryNull,
                                    onCheckedChange = { viewModel.setSpoofCountryNull(it) }
                                )

                                // Country Code Input
                                AnimatedVisibility(
                                    visible = !uiState.spoofCountryNull,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .background(colors.backgroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_country_spoofing_code_label),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textWeak,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        
                                        var currentCode by remember(uiState.spoofCountryCode) { 
                                            mutableStateOf(uiState.spoofCountryCode) 
                                        }

                                        SmoothOutlinedTextField(
                                            value = currentCode,
                                            onValueChange = { 
                                                if (it.length <= 2) {
                                                    currentCode = it.uppercase()
                                                    viewModel.setSpoofCountryCode(currentCode)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text(stringResource(R.string.settings_country_spoofing_code_hint), color = colors.textWeak) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colors.brandNorm,
                                                unfocusedBorderColor = colors.separatorNorm,
                                                focusedTextColor = colors.textNorm,
                                                unfocusedTextColor = colors.textNorm,
                                                cursorColor = colors.brandNorm
                                            ),
                                            textStyle = MaterialTheme.typography.bodyMedium
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Button(
                                            onClick = {
                                                viewModel.refreshServersAfterSpoofChange()
                                                Toast.makeText(context, R.string.settings_country_spoofing_update_toast, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.brandNorm,
                                                contentColor = colors.textInverted
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.ota_btn_check))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
