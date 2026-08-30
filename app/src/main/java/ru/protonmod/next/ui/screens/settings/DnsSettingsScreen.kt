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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.network.dns.DnsProviders
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()

    // Internal state for selection and input
    var useDefaultDns by remember(uiState.customDns) { mutableStateOf(uiState.customDns.isBlank()) }
    var inputText by remember(uiState.customDns) { mutableStateOf(uiState.customDns) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient (Fullscreen)
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
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.settings_custom_dns),
                        onBack = onBack
                    )

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
                                imageVector = ProtonIcons.Servers,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = colors.brandNorm
                            )
                        }
                    }

                    // Title
                    Text(
                        text = stringResource(R.string.settings_custom_dns),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center,
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = stringResource(R.string.settings_custom_dns_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = contentModifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }

                item(contentType = "ProviderSelection") {
                    Box(
                        modifier = contentModifier
                            .padding(horizontal = 16.dp)
                            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            // Section Title
                            Text(
                                text = stringResource(R.string.settings_dns_provider_title).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textWeak,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                            )

                            // Automatic ordering
                            ProviderRow(
                                title = stringResource(R.string.settings_dns_provider_auto),
                                subtitle = stringResource(R.string.settings_dns_provider_auto_desc),
                                selected = DnsProviders.byId(uiState.dnsProviderId) == null,
                                onClick = { viewModel.setDnsProviderId("") }
                            )

                            DnsSectionDivider()

                            DnsProviders.ALL.forEachIndexed { index, provider ->
                                ProviderRow(
                                    title = provider.displayName,
                                    subtitle = stringResource(
                                        R.string.settings_dns_provider_jurisdiction,
                                        provider.jurisdiction
                                    ),
                                    selected = uiState.dnsProviderId == provider.id,
                                    onClick = { viewModel.setDnsProviderId(provider.id) }
                                )
                                if (index != DnsProviders.ALL.lastIndex) {
                                    DnsSectionDivider()
                                }
                            }

                            Text(
                                text = stringResource(R.string.settings_dns_provider_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textWeak,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
                            )
                        }
                    }
                }

                item(contentType = "DotFallback") {
                    // Mandatory inside Russia: DoH on 443 is filtered there, so 853
                    // is frequently the last encrypted path. The switch is shown
                    // locked on rather than hidden, so the behaviour stays visible.
                    val locked = uiState.isRussianRegion

                    Box(
                        modifier = contentModifier
                            .padding(horizontal = 16.dp)
                            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !locked) {
                                        viewModel.setDnsOverTlsFallbackEnabled(!uiState.dnsOverTlsFallbackEnabled)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_dns_dot_fallback),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = colors.textNorm
                                    )
                                    Text(
                                        text = if (locked) {
                                            stringResource(R.string.settings_dns_dot_locked_region)
                                        } else {
                                            stringResource(R.string.settings_dns_dot_fallback_desc)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textWeak,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = locked || uiState.dnsOverTlsFallbackEnabled,
                                    onCheckedChange = { viewModel.setDnsOverTlsFallbackEnabled(it) },
                                    enabled = !locked,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.textInverted,
                                        checkedTrackColor = colors.brandNorm,
                                        uncheckedThumbColor = colors.shade60,
                                        uncheckedTrackColor = colors.shade20,
                                        uncheckedBorderColor = Color.Transparent,
                                        disabledCheckedThumbColor = colors.textInverted,
                                        disabledCheckedTrackColor = colors.brandNorm,
                                    )
                                )
                            }
                        }
                    }
                }

                item(contentType = "ModeSelection") {
                    Box(
                        modifier = contentModifier
                            .padding(horizontal = 16.dp)
                            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            // Section Title
                            Text(
                                text = stringResource(R.string.settings_custom_dns_title).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textWeak,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                            )

                            // Default Mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { useDefaultDns = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_custom_dns_default),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = colors.textNorm
                                    )
                                }
                                RadioButton(
                                    selected = useDefaultDns,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colors.separatorNorm.copy(alpha = 0.5f)
                            )

                            // Custom Mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { useDefaultDns = false }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_custom_dns),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textNorm,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioButton(
                                    selected = !useDefaultDns,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                                )
                            }

                            // Animated Custom Input
                            AnimatedVisibility(
                                visible = !useDefaultDns,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .background(colors.backgroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    SmoothOutlinedTextField(
                                        value = inputText,
                                        onValueChange = {
                                            inputText = it
                                            viewModel.clearCustomDnsRejection()
                                        },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.settings_custom_dns_placeholder),
                                                color = colors.textWeak.copy(alpha = 0.5f)
                                            )
                                        },
                                        singleLine = true,
                                        isError = uiState.customDnsRejected,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.brandNorm,
                                            unfocusedBorderColor = colors.separatorNorm,
                                            focusedTextColor = colors.textNorm,
                                            unfocusedTextColor = colors.textNorm,
                                            cursorColor = colors.brandNorm,
                                            errorBorderColor = colors.notificationError
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    AnimatedVisibility(
                                        visible = uiState.customDnsRejected,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_dns_rejected_russian),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.notificationError,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(contentType = "Actions") {
                    val isChanged = remember(useDefaultDns, inputText, uiState.customDns) {
                        val currentEffectiveDns = if (useDefaultDns) "" else inputText.trim()
                        currentEffectiveDns != uiState.customDns
                    }

                    Column(
                        modifier = contentModifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val finalDns = if (useDefaultDns) "" else inputText.trim()
                                viewModel.setCustomDns(finalDns)
                                // The store is authoritative and refuses Russian
                                // resolvers. Checking here as well keeps the screen
                                // open on rejection so the reason stays visible.
                                if (!DnsProviders.isDenied(finalDns)) {
                                    onBack()
                                }
                            },
                            enabled = isChanged || !useDefaultDns && inputText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandNorm,
                                disabledContainerColor = colors.brandNorm.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                stringResource(R.string.btn_save),
                                color = colors.textInverted,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (uiState.customDns.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.setCustomDns("")
                                    onBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.settings_custom_dns_reset),
                                    color = colors.notificationError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textNorm
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
        )
    }
}

@Composable
private fun DnsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = ProtonNextTheme.colors.separatorNorm.copy(alpha = 0.5f)
    )
}
