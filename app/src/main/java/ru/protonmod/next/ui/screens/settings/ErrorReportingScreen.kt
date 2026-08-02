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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorReportingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    if (!BuildConfig.SENTRY_ENABLED) {
        LaunchedEffect(onBack) { onBack() }
        return
    }

    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()

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
            // Background gradient decoration (immersive)
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
                    title = stringResource(R.string.settings_error_reporting),
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
                            imageVector = ProtonIcons.Bug,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.settings_error_reporting),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(R.string.settings_error_reporting_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = contentModifier
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Section Title
                        Text(
                            text = stringResource(R.string.settings_privacy).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textWeak,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_crash_reports),
                            subtitle = stringResource(R.string.settings_crash_reports_desc),
                            icon = ProtonIcons.Bug,
                            checked = uiState.isCrashReportsEnabled,
                            onCheckedChange = { viewModel.setCrashReportsEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_non_fatal),
                            subtitle = stringResource(R.string.settings_sentry_non_fatal_desc),
                            icon = ProtonIcons.ExclamationTriangleFilled,
                            checked = uiState.isSentryNonFatalEnabled,
                            onCheckedChange = { viewModel.setSentryNonFatalEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_anr),
                            subtitle = stringResource(R.string.settings_sentry_anr_desc),
                            icon = ProtonIcons.Hourglass,
                            checked = uiState.isSentryAnrEnabled,
                            onCheckedChange = { viewModel.setSentryAnrEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_metrics),
                            subtitle = stringResource(R.string.settings_sentry_metrics_desc),
                            icon = ProtonIcons.ChartLine,
                            checked = uiState.isSentryMetricsEnabled,
                            onCheckedChange = { viewModel.setSentryMetricsEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_logs),
                            subtitle = stringResource(R.string.settings_sentry_logs_desc),
                            icon = ProtonIcons.ChartLine,
                            checked = uiState.isSentryLogsEnabled,
                            onCheckedChange = { viewModel.setSentryLogsEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_performance),
                            subtitle = stringResource(R.string.settings_sentry_performance_desc),
                            icon = ProtonIcons.Bolt,
                            checked = uiState.isSentryPerformanceEnabled,
                            onCheckedChange = { viewModel.setSentryPerformanceEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_analytics),
                            subtitle = stringResource(R.string.settings_analytics_desc),
                            icon = ProtonIcons.ChartLine,
                            checked = uiState.isAnalyticsEnabled,
                            onCheckedChange = { viewModel.setAnalyticsEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingToggleRow(
                            title = stringResource(R.string.settings_sentry_session_replay),
                            subtitle = stringResource(R.string.settings_sentry_session_replay_desc),
                            icon = ProtonIcons.ArrowRotateRight,
                            checked = uiState.isSentrySessionReplayEnabled,
                            onCheckedChange = { viewModel.setSentrySessionReplayEnabled(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SentryPoweredBy()
            }
        }
    }
}
