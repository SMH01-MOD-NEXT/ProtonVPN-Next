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

package ru.protonmod.next.ui.screens.netshield

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.R
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShieldSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetShieldSettingsViewModel = hiltViewModel(),
) {
    val colors = ProtonNextTheme.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tablet = isTablet()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // hosts/adblock lists are plain text, but pickers report many MIME types for .txt files.
    val filterFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (!content.isNullOrBlank()) viewModel.importCustomFilters(content)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
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
                val contentModifier = if (tablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.netshield_title),
                        onBack = onBack,
                    )

                    Box(
                        modifier = contentModifier.padding(top = 24.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .background(colors.brandNorm.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_proton_netshield),
                                contentDescription = null,
                                modifier = Modifier.size(58.dp),
                                tint = colors.brandNorm,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.netshield_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center,
                        modifier = contentModifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.netshield_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = contentModifier.padding(horizontal = 32.dp),
                    )
                }

                item(contentType = "ProtectionLevel") {
                    Column(
                        modifier = contentModifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionTitle(stringResource(R.string.netshield_protection_level_title))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    shape = RoundedCornerShape(20.dp),
                                    alpha = 0.4f,
                                    shadowElevation = 0.dp,
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp).animateContentSize()
                            ) {
                                NetShieldLevel.entries.forEachIndexed { index, level ->
                                    LevelRow(
                                        level = level,
                                        selected = state.level == level,
                                        onClick = { viewModel.setLevel(level) },
                                    )
                                    if (index != NetShieldLevel.entries.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = colors.separatorNorm.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(contentType = "FilterLists") {
                    Column(
                        modifier = contentModifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionTitle(stringResource(R.string.netshield_lists_title))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    shape = RoundedCornerShape(20.dp),
                                    alpha = 0.4f,
                                    shadowElevation = 0.dp,
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(colors.brandNorm.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = ProtonIcons.Cloud,
                                            contentDescription = null,
                                            tint = colors.brandNorm,
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.netshield_lists_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textNorm,
                                        )
                                        Text(
                                            text = listStatus(
                                                isUpdating = state.lists.isUpdating,
                                                lastUpdatedAt = state.lists.lastUpdatedAt,
                                                domainCount = state.lists.domainCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textWeak,
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = state.lists.error != null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.netshield_lists_error,
                                            state.lists.error.orEmpty(),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.notificationError,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.notificationError.copy(alpha = 0.1f))
                                            .padding(12.dp),
                                    )
                                }

                                Button(
                                    onClick = viewModel::updateLists,
                                    enabled = !state.lists.isUpdating,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.brandNorm,
                                        contentColor = colors.textInverted,
                                        disabledContainerColor = colors.brandNorm.copy(alpha = 0.55f),
                                        disabledContentColor = colors.textInverted,
                                    ),
                                ) {
                                    if (state.lists.isUpdating) {
                                        ExpressiveCircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            color = colors.textInverted,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.netshield_lists_updating))
                                    } else {
                                        Icon(ProtonIcons.ArrowsRotate, contentDescription = null)
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.netshield_update_lists))
                                    }
                                }
                            }
                        }
                    }
                }

                item(contentType = "CustomFilters") {
                    NetShieldCustomFiltersSection(
                        state = state,
                        onImportText = viewModel::importCustomFilters,
                        onImportUrl = viewModel::importCustomFiltersFromUrl,
                        onPickFile = { filterFilePicker.launch(arrayOf("text/*", "application/octet-stream")) },
                        onClear = viewModel::clearCustomFilters,
                        modifier = contentModifier,
                    )
                }

                item(contentType = "FilterSources") {
                    NetShieldSourcesSection(
                        state = state,
                        onPresetSelected = viewModel::setCategoryPreset,
                        onCustomUrl = viewModel::setCategoryUrl,
                        onResetCategory = viewModel::resetCategorySource,
                        onApplyToAll = viewModel::applyPresetToAll,
                        onResetAll = viewModel::resetAllSources,
                        modifier = contentModifier,
                    )
                }

                item(contentType = "StatisticsNote") {
                    Row(
                        modifier = contentModifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.brandNorm.copy(alpha = 0.09f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = ProtonIcons.InfoCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colors.brandNorm,
                        )
                        Text(
                            text = stringResource(R.string.netshield_saved_estimate_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textWeak,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = ProtonNextTheme.colors.textWeak,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun LevelRow(level: NetShieldLevel, selected: Boolean, onClick: () -> Unit) {
    val colors = ProtonNextTheme.colors
    val title = when (level) {
        NetShieldLevel.DISABLED -> R.string.netshield_level_off
        NetShieldLevel.MALWARE -> R.string.netshield_level_malware
        NetShieldLevel.ADS_TRACKERS -> R.string.netshield_level_extended
        NetShieldLevel.ADS_TRACKERS_ADULT -> R.string.netshield_level_adult
    }
    val description = when (level) {
        NetShieldLevel.DISABLED -> R.string.netshield_level_off_desc
        NetShieldLevel.MALWARE -> R.string.netshield_level_malware_desc
        NetShieldLevel.ADS_TRACKERS -> R.string.netshield_level_extended_desc
        NetShieldLevel.ADS_TRACKERS_ADULT -> R.string.netshield_level_adult_desc
    }
    val background by animateColorAsState(
        targetValue = if (selected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundNorm.copy(alpha = 0f),
        label = "netshield_level_background",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textNorm,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm),
        )
    }
}

@Composable
private fun listStatus(isUpdating: Boolean, lastUpdatedAt: Long, domainCount: Int): String = when {
    isUpdating -> stringResource(R.string.netshield_lists_updating)
    lastUpdatedAt > 0 -> stringResource(
        R.string.netshield_lists_updated,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastUpdatedAt)),
        domainCount,
    )
    else -> stringResource(R.string.netshield_lists_never_updated)
}
