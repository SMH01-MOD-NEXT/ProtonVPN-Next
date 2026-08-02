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

package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import ru.protonmod.next.R
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

/**
 * Dashboard statistics card - a direct port of the desktop stats slider.
 * Three slides (Traffic / Analytics / Usage time), chevron navigation and
 * an eye toggle that enables/disables statistics collection.
 */
@Composable
fun StatsCard(
    stats: TrafficStatsUiState,
    isConnected: Boolean,
    liveSpeed: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var slide by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header: slide icon + title, chevrons, eye toggle.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, title) = when (slide) {
                0 -> ProtonIcons.ChartLine to stringResource(R.string.stats_title_traffic)
                1 -> ProtonIcons.ChartLine to stringResource(R.string.stats_title_analytics)
                else -> ProtonIcons.Clock to stringResource(R.string.stats_title_usage)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ProtonNextTheme.colors.brandNorm,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = ProtonNextTheme.colors.textWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
            IconButton(onClick = { slide = (slide + 2) % 3 }, modifier = Modifier.size(28.dp)) {
                Icon(
                    ProtonIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.stats_prev_desc),
                    tint = ProtonNextTheme.colors.iconWeak
                )
            }
            IconButton(onClick = { slide = (slide + 1) % 3 }, modifier = Modifier.size(28.dp)) {
                Icon(
                    ProtonIcons.ChevronRight,
                    contentDescription = stringResource(R.string.stats_next_desc),
                    tint = ProtonNextTheme.colors.iconWeak
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (stats.enabled) ProtonIcons.Eye else ProtonIcons.EyeSlash,
                    contentDescription = stringResource(R.string.stats_toggle_desc),
                    tint = ProtonNextTheme.colors.iconWeak,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (!stats.enabled) {
                StatsDisabledOverlay(onEnable = onToggle)
            } else {
                Crossfade(targetState = slide, label = "stats_slide") { current ->
                    when (current) {
                        0 -> TrafficSlide(stats, isConnected, liveSpeed)
                        1 -> AnalyticsSlide(stats)
                        else -> UsageSlide(stats)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsDisabledOverlay(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            ProtonIcons.EyeSlash,
            contentDescription = null,
            tint = ProtonNextTheme.colors.iconWeak,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = stringResource(R.string.stats_disabled),
            fontSize = 13.sp,
            color = ProtonNextTheme.colors.textWeak,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(onClick = onEnable) {
            Text(
                text = stringResource(R.string.stats_enable),
                color = ProtonNextTheme.colors.brandNorm,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrafficSlide(
    stats: TrafficStatsUiState,
    isConnected: Boolean,
    liveSpeed: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatRow(stringResource(R.string.stats_today), stats.today)
        StatRow(stringResource(R.string.stats_month), stats.month)
        StatRow(stringResource(R.string.stats_year), stats.year)

        if (isConnected && liveSpeed != null) {
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ProtonNextTheme.colors.notificationSuccess.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stats_live_connection).uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = ProtonNextTheme.colors.notificationSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = liveSpeed,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ProtonNextTheme.colors.textNorm
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, summary: TrafficPeriodSummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ProtonNextTheme.colors.textWeak,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "\u2193 " + formatStatBytes(summary.rxBytes),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = ProtonNextTheme.colors.notificationSuccess
        )
        Text(
            text = "\u2191 " + formatStatBytes(summary.txBytes),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = ProtonNextTheme.colors.brandNorm,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun AnalyticsSlide(stats: TrafficStatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChartBlock(
            label = stringResource(R.string.stats_daily_chart),
            points = stats.dailyChart,
            color = ProtonNextTheme.colors.brandNorm,
            chartHeight = 60.dp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartBlock(
                label = stringResource(R.string.stats_monthly_chart),
                points = stats.monthlyChart,
                color = ProtonNextTheme.colors.notificationSuccess,
                chartHeight = 35.dp,
                modifier = Modifier.weight(1f)
            )
            ChartBlock(
                label = stringResource(R.string.stats_yearly_chart),
                points = stats.yearlyChart,
                color = ProtonNextTheme.colors.notificationWarning,
                chartHeight = 35.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChartBlock(
    label: String,
    points: ImmutableList<TrafficChartPoint>,
    color: Color,
    chartHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = ProtonNextTheme.colors.textWeak
        )
        SmoothChart(
            points = points,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(top = 4.dp)
        )
    }
}

/** Normalizes chart point totals to 0..1 fractions of the maximum value. */
private fun normalizeChartPoints(points: List<TrafficChartPoint>): List<Float> {
    val maxValue = points.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L)?.toFloat() ?: 1f
    return points.map { point -> point.totalBytes.toFloat() / maxValue }
}

/**
 * Smooth cubic-bezier area chart - port of the desktop SimpleChart
 * (control point offset = dx / 2.5, area gradient + stroke line).
 */
@Composable
private fun SmoothChart(
    points: ImmutableList<TrafficChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Normalized point heights (0..1), recomputed only when the data changes.
    val normalized = remember(points) { normalizeChartPoints(points) }

    Canvas(modifier = modifier) {
        if (normalized.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        val stepX = size.width / (normalized.size - 1)
        // Keep 5% padding at top and bottom, exactly like the desktop chart.
        val usableHeight = size.height * 0.9f
        fun yAt(index: Int): Float = size.height * 0.95f - normalized[index] * usableHeight

        val line = Path().apply {
            moveTo(0f, yAt(0))
            for (i in 1 until normalized.size) {
                val x0 = (i - 1) * stepX
                val x1 = i * stepX
                val cpDx = (x1 - x0) / 2.5f
                cubicTo(x0 + cpDx, yAt(i - 1), x1 - cpDx, yAt(i), x1, yAt(i))
            }
        }

        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0f))
            )
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun UsageSlide(stats: TrafficStatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        UsageRow(stringResource(R.string.stats_today), stats.today.usageSeconds)
        UsageRow(stringResource(R.string.stats_month), stats.month.usageSeconds)
        UsageRow(stringResource(R.string.stats_year), stats.year.usageSeconds)
    }
}

@Composable
private fun UsageRow(label: String, seconds: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ProtonNextTheme.colors.textWeak,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = ProtonIcons.Clock,
            contentDescription = null,
            tint = ProtonNextTheme.colors.iconWeak,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = formatStatDuration(seconds),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = ProtonNextTheme.colors.textNorm,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
