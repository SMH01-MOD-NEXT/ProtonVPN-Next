/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

private val rotationIntervals = listOf(5, 15, 30, 60)

@Composable
fun IpRotationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IpRotationSettingsViewModel = hiltViewModel(),
) {
    val colors = ProtonNextTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tablet = isTablet()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            colors.brandNorm.copy(alpha = 0.25f),
                            colors.backgroundNorm.copy(alpha = 0.1f),
                            colors.backgroundNorm,
                        )
                    )
                )
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                horizontalAlignment = if (tablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val content = if (tablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()
                item(contentType = "Header") {
                    NavigationHeader(stringResource(R.string.ip_rotation_title), onBack)
                    Box(content.padding(top = 24.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(104.dp).clip(CircleShape)
                                .background(colors.brandNorm.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                ProtonIcons.ArrowsRotate,
                                contentDescription = null,
                                tint = colors.brandNorm,
                                modifier = Modifier.size(58.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.ip_rotation_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center,
                        modifier = content.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.ip_rotation_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = content.padding(horizontal = 32.dp),
                    )
                }

                item(contentType = "MainSwitch") {
                    RotationSection(stringResource(R.string.ip_rotation_section_status), content) {
                        RotationToggle(
                            R.string.ip_rotation_enable,
                            R.string.ip_rotation_enable_desc,
                            state.enabled,
                            viewModel::setEnabled,
                        )
                    }
                }

                item(contentType = "Options") {
                    AnimatedVisibility(
                        visible = state.enabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            RotationSection(stringResource(R.string.ip_rotation_interval), content) {
                                rotationIntervals.forEachIndexed { index, minutes ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { viewModel.setIntervalMinutes(minutes) }
                                            .padding(horizontal = 16.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.ip_rotation_minutes, minutes),
                                            color = colors.textNorm,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        RadioButton(
                                            selected = state.intervalMinutes == minutes,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm),
                                        )
                                    }
                                    if (index != rotationIntervals.lastIndex) RotationDivider()
                                }
                            }
                            RotationSection(stringResource(R.string.ip_rotation_location), content) {
                                RotationToggle(
                                    R.string.ip_rotation_keep_country,
                                    R.string.ip_rotation_keep_country_desc,
                                    state.keepCountry,
                                    viewModel::setKeepCountry,
                                )
                            }
                        }
                    }
                }

                item(contentType = "Note") {
                    Text(
                        stringResource(R.string.ip_rotation_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        modifier = content.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationSection(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = ProtonNextTheme.colors
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.textWeak, modifier = Modifier.padding(start = 8.dp))
        Column(
            Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun RotationToggle(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ProtonNextTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = colors.textNorm, fontWeight = FontWeight.Medium)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RotationDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = ProtonNextTheme.colors.separatorNorm.copy(alpha = 0.5f))
}
