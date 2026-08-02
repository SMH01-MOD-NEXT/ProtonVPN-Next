/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.vpn.AmneziaVpnManager

/** Tablet-only, Compose-native 1:1 port of the Desktop DashboardScreen. */
@Composable
internal fun DesktopTabletDashboard(
    state: DashboardUiState.Success,
    stats: TrafficStatsUiState,
    onServerClick: (LogicalServer) -> Unit,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefreshCert: () -> Unit,
    onToggleIpVisibility: () -> Unit,
    onChangeQuickConnect: () -> Unit,
    onToggleStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entered by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        LaunchedEffect(Unit) { entered = true }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val leftWidth = 380.dp.coerceAtMost(maxWidth * 0.42f)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier.width(leftWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    DesktopEnter(visible = entered, delayMillis = 0) {
                        DesktopConnectionCard(
                            state = state,
                            onQuickConnect = onQuickConnect,
                            onDisconnect = onDisconnect,
                            onChangeQuickConnect = onChangeQuickConnect
                        )
                    }
                    DesktopEnter(
                        visible = entered,
                        delayMillis = 100,
                        modifier = Modifier.weight(1f)
                    ) {
                        RecentConnectionsCard(
                            recents = state.recentConnections.take(10).toImmutableList(),
                            connectedServerId = state.connectedServer?.id,
                            onServerClick = onServerClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DesktopEnter(visible = entered, delayMillis = 200) {
                        DesktopStatsCard(
                            stats = stats,
                            isConnected = state.isConnected,
                            liveSpeed = state.speed,
                            onToggle = onToggleStats,
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                    }
                }

                DesktopEnter(
                    visible = entered,
                    delayMillis = 300,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    DesktopMapPanel(
                        state = state,
                        onToggleIpVisibility = onToggleIpVisibility,
                        onResume = onResume,
                        onRefreshCert = onRefreshCert,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopEnter(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(360, delayMillis)) + slideInVertically(tween(420, delayMillis)) { it / 12 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 16 },
        modifier = modifier,
    ) { content() }
}

@Composable
private fun DesktopConnectionCard(
    state: DashboardUiState.Success,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChangeQuickConnect: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val targetServer = state.servers.find { it.id == state.quickConnectTargetId }
    val selectedProfile = state.profiles.find { it.id == state.quickConnectTargetId }
    val connectedServer = state.connectedServer
    val isBusy = state.isConnecting || state.vpnState == AmneziaVpnManager.VpnState.DISCONNECTING

    val vpnStatusStr = stringResource(R.string.status_vpn)
    val recentConnectionsStr = stringResource(R.string.title_recent_connections)
    val fastestServerStr = stringResource(R.string.label_fastest_server)

    val title = remember(state.isConnected, state.isConnecting, state.quickConnectStrategy, connectedServer, targetServer, selectedProfile, vpnStatusStr, recentConnectionsStr, fastestServerStr) {
        when {
            state.isConnected || state.isConnecting -> connectedServer?.let {
                CountryUtils.getCountryName(context, it.exitCountry)
            } ?: vpnStatusStr
            state.quickConnectStrategy == "recent" -> recentConnectionsStr
            state.quickConnectStrategy == "profile" -> selectedProfile?.name ?: fastestServerStr
            state.quickConnectStrategy == "server" && targetServer != null -> CountryUtils.getCountryName(context, targetServer.exitCountry)
            else -> fastestServerStr
        }
    }

    val lastUsedStr = stringResource(R.string.qc_last_used)
    val lowestLoadStr = stringResource(R.string.qc_lowest_load)

    val subtitle = remember(state.isConnected, state.isConnecting, connectedServer, state.quickConnectStrategy, targetServer, lastUsedStr, lowestLoadStr) {
        when {
            state.isConnected || state.isConnecting -> listOfNotNull(
                connectedServer?.localizedCity ?: connectedServer?.city,
                connectedServer?.name
            ).filter { it.isNotBlank() }.joinToString(", ")
            state.quickConnectStrategy == "recent" -> lastUsedStr
            state.quickConnectStrategy == "server" && targetServer != null -> listOf(targetServer.localizedCity ?: targetServer.city, targetServer.name).filter { it.isNotBlank() }.joinToString(", ")
            else -> lowestLoadStr
        }
    }
    val flagCode = when {
        state.isConnected || state.isConnecting -> connectedServer?.exitCountry
        state.quickConnectStrategy == "server" -> targetServer?.exitCountry
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = (if (state.isConnected) stringResource(R.string.dashboard_protected) else stringResource(R.string.label_select_location)).uppercase(),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = if (state.isConnected) colors.notificationSuccess else colors.notificationError
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChangeQuickConnect)
                .padding(top = 12.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (state.isConnected) colors.notificationSuccess else colors.brandNorm).copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val flag = flagCode?.let { CountryUtils.getFlagResource(context, it) } ?: 0
                if (flag != 0) {
                    FlagIcon(countryFlag = flag, size = DpSize(40.dp, 40.dp))
                } else if (!state.isConnected && state.quickConnectStrategy == "fastest") {
                    FlagIcon(
                        countryFlag = R.drawable.flag_fastest,
                        size = DpSize(28.dp, 20.dp)
                    )
                } else {
                    Icon(
                        imageVector = when (state.quickConnectStrategy) {
                            "profile" -> ProtonIcons.Star
                            "recent" -> ProtonIcons.ClockRotateLeft
                            else -> if (state.isConnected) ProtonIcons.Globe else ProtonIcons.Bolt
                        },
                        contentDescription = null,
                        tint = if (state.isConnected) colors.notificationSuccess else colors.brandNorm,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textWeak,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = ProtonIcons.ChevronRight,
                contentDescription = stringResource(R.string.desc_change_server),
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.size(18.dp)
            )
        }
        DesktopActionButton(
            connectedOrConnecting = state.isConnected || state.isConnecting,
            busy = isBusy,
            onClick = if (state.isConnected || state.isConnecting) onDisconnect else onQuickConnect
        )
    }
}

@Composable
private fun DesktopActionButton(
    connectedOrConnecting: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "dashboard_button_scale")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (connectedOrConnecting) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f)))
                else Brush.horizontalGradient(listOf(colors.brandNorm, Color(0xFF8B5CF6)))
            )
            .then(if (connectedOrConnecting) Modifier.border(1.dp, colors.notificationError.copy(alpha = 0.20f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                ExpressiveCircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (connectedOrConnecting) colors.notificationError else Color.White
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (connectedOrConnecting) stringResource(R.string.btn_disconnect) else stringResource(R.string.btn_quick_connect),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (connectedOrConnecting) colors.notificationError else Color.White
            )
        }
    }
}

@Composable
private fun DesktopMapPanel(
    state: DashboardUiState.Success,
    onToggleIpVisibility: () -> Unit,
    onResume: () -> Unit,
    onRefreshCert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
    ) {
        HomeMap(
            allServers = state.servers.toImmutableList(),
            connectedServer = state.connectedServer,
            isConnected = state.isConnected,
            isConnecting = state.isConnecting,
            userCountryCode = state.originalLocationText?.countryCode,
            isInteractive = true,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DesktopStatusPill(state = state)
        }
        val location = if (state.isConnected) state.vpnLocationText else state.originalLocationText
        if (location != null) {
            DesktopMapLocationOverlay(
                locationText = location,
                isProtected = state.isConnected,
                isIpHidden = state.isIpHidden,
                onToggleIpVisibility = onToggleIpVisibility,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp).widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CertificateBanner(state = state.certificateState, onRefresh = onRefreshCert)
            if (state.isBatteryOptimized) {
                BatteryOptimizationBanner()
            }
            state.connectionWarning?.let { warning ->
                ConnectionWarningBanner(warning = warning)
            }
            if (state.pauseEndTime > System.currentTimeMillis()) {
                PauseBanner(endTime = state.pauseEndTime, onResume = onResume)
            }
            if (state.netShieldLevel.enabled) {
                NetShieldStatsCard(
                    stats = state.netShieldStats,
                    level = state.netShieldLevel,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun DesktopStatusPill(state: DashboardUiState.Success, modifier: Modifier = Modifier) {
    val colors = ProtonNextTheme.colors
    val (icon, tint, label) = when (state.vpnState) {
        AmneziaVpnManager.VpnState.CONNECTED -> Triple(R.drawable.ic_proton_lock_filled, colors.notificationSuccess, stringResource(R.string.dashboard_protected))
        AmneziaVpnManager.VpnState.CONNECTING, AmneziaVpnManager.VpnState.VERIFYING -> Triple(R.drawable.ic_proton_lock_open_filled_2, colors.brandNorm, stringResource(R.string.dashboard_connecting))
        AmneziaVpnManager.VpnState.DISCONNECTING -> Triple(R.drawable.ic_proton_lock_open_filled_2, colors.brandNorm, stringResource(R.string.status_disconnecting))
        else -> Triple(R.drawable.ic_proton_lock_open_filled_2, colors.notificationError, stringResource(R.string.dashboard_unprotected))
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color.White)
    }
}

@Composable
private fun DesktopMapLocationOverlay(
    locationText: LocationText,
    isProtected: Boolean,
    isIpHidden: Boolean,
    onToggleIpVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    Column(
        modifier = modifier
            .widthIn(min = 200.dp)
            .liquidGlass(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleIpVisibility)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val flag = locationText.countryCode?.let { CountryUtils.getFlagResource(context, it) } ?: 0
            if (flag != 0) FlagIcon(countryFlag = flag, size = DpSize(22.dp, 16.dp))
            Text(locationText.country, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(
            stringResource(R.string.dashboard_ip_address).uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.padding(top = 6.dp)
        )
        val ip = remember(locationText.ip, isIpHidden) {
            if (isIpHidden) locationText.ip.map { if (it == '.' || it == ':') it else '*' }.joinToString("") else locationText.ip
        }
        Text(
            ip,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isProtected) colors.notificationSuccess else colors.notificationError,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DesktopStatsCard(
    stats: TrafficStatsUiState,
    isConnected: Boolean,
    liveSpeed: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    var slide by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = modifier.liquidGlass(shape = RoundedCornerShape(24.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val icon = when (slide) { 0 -> ProtonIcons.ChartLine; 1 -> ProtonIcons.ChartLine; else -> ProtonIcons.Clock }
            val title = when (slide) { 0 -> R.string.stats_title_traffic; 1 -> R.string.stats_title_analytics; else -> R.string.stats_title_usage }
            Icon(icon, null, tint = colors.brandNorm, modifier = Modifier.size(16.dp))
            Text(
                stringResource(title).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            DesktopStatIconButton({ slide = (slide + 2) % 3 }) { Icon(ProtonIcons.ChevronLeft, stringResource(R.string.stats_prev_desc), tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(6.dp))
            DesktopStatIconButton({ slide = (slide + 1) % 3 }) { Icon(ProtonIcons.ChevronRight, stringResource(R.string.stats_next_desc), tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            DesktopStatIconButton(onToggle) {
                Icon(if (stats.enabled) ProtonIcons.Eye else ProtonIcons.EyeSlash, stringResource(R.string.stats_toggle_desc), tint = if (stats.enabled) colors.brandNorm else colors.textWeak, modifier = Modifier.size(16.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
            Crossfade(targetState = slide, animationSpec = tween(320), label = "desktop_stats_slide") { current ->
                when (current) {
                    0 -> DesktopTrafficSlide(stats, isConnected, liveSpeed)
                    1 -> DesktopAnalyticsSlide(stats)
                    else -> DesktopUsageSlide(stats)
                }
            }
            if (!stats.enabled) {
                Column(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.62f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.stats_disabled), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = onToggle) { Text(stringResource(R.string.stats_enable), color = colors.brandNorm, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun DesktopStatIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DesktopTrafficSlide(stats: TrafficStatsUiState, isConnected: Boolean, liveSpeed: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesktopStatRow(stringResource(R.string.stats_today), stats.today)
        DesktopStatRow(stringResource(R.string.stats_month), stats.month)
        DesktopStatRow(stringResource(R.string.stats_year), stats.year)
        if (isConnected && liveSpeed != null) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ProtonNextTheme.colors.brandNorm.copy(alpha = 0.08f))
                    .border(1.dp, ProtonNextTheme.colors.brandNorm.copy(alpha = 0.20f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.stats_live_connection).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = ProtonNextTheme.colors.brandNorm, modifier = Modifier.weight(1f))
                Text(liveSpeed, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun DesktopStatRow(label: String, value: TrafficPeriodSummary) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = colors.textWeak, modifier = Modifier.weight(1f))
        Text("↓${formatStatBytes(value.rxBytes)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.brandNorm)
        Text("↑${formatStatBytes(value.txBytes)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.notificationSuccess, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun DesktopAnalyticsSlide(stats: TrafficStatsUiState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopChartBlock(stringResource(R.string.stats_daily_chart), stats.dailyChart, ProtonNextTheme.colors.brandNorm, Modifier.weight(1.5f))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesktopChartBlock(stringResource(R.string.stats_month), stats.monthlyChart, ProtonNextTheme.colors.notificationSuccess, Modifier.weight(1f))
            DesktopChartBlock(stringResource(R.string.stats_year), stats.yearlyChart, ProtonNextTheme.colors.notificationWarning, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DesktopChartBlock(label: String, points: ImmutableList<TrafficChartPoint>, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.50f))
        DesktopSmoothChart(points, color, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
    }
}

@Composable
private fun DesktopSmoothChart(points: ImmutableList<TrafficChartPoint>, color: Color, modifier: Modifier = Modifier) {
    val max = remember(points) { points.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1024L)?.toFloat() ?: 1024f }
    val values = remember(points, max) { points.map { it.totalBytes / max } }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val dx = size.width / (values.size - 1)
        fun y(i: Int) = size.height - values[i] * size.height
        val line = Path().apply {
            moveTo(0f, y(0))
            for (i in 1 until values.size) {
                val x0 = (i - 1) * dx; val x1 = i * dx; val cp = dx / 2.5f
                cubicTo(x0 + cp, y(i - 1), x1 - cp, y(i), x1, y(i))
            }
        }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun DesktopUsageSlide(stats: TrafficStatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DesktopUsageRow(stringResource(R.string.stats_today), stats.today.usageSeconds)
        DesktopUsageRow(stringResource(R.string.stats_month), stats.month.usageSeconds)
        DesktopUsageRow(stringResource(R.string.stats_year), stats.year.usageSeconds)
    }
}

@Composable
private fun DesktopUsageRow(label: String, seconds: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ProtonIcons.Clock, null, tint = ProtonNextTheme.colors.brandNorm.copy(alpha = 0.60f), modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, color = ProtonNextTheme.colors.textWeak, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(formatStatDuration(seconds), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
