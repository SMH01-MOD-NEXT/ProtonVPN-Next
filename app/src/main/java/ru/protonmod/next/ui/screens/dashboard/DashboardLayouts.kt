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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
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
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils

/**
 * Tablet layout - a 1:1 port of the desktop dashboard grid:
 * a fixed 380dp left column (connection card, recent connections card,
 * 240dp stats slider) and the interactive map filling the remaining space
 * with the desktop-style location overlay in the bottom-right corner.
 */
@Composable
internal fun TabletDashboardLayout(
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
) {
    DesktopTabletDashboard(
        state = state,
        stats = stats,
        onServerClick = onServerClick,
        onQuickConnect = onQuickConnect,
        onDisconnect = onDisconnect,
        onPause = onPause,
        onResume = onResume,
        onRefreshCert = onRefreshCert,
        onToggleIpVisibility = onToggleIpVisibility,
        onChangeQuickConnect = onChangeQuickConnect,
        onToggleStats = onToggleStats
    )
}

/**
 * Phone layout: map on top, then the connection block (current server +
 * default-connection selector + connect button), then the statistics card.
 */
@Composable
internal fun PhoneDashboardLayout(
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
) {
    var showRecentsSheet by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
    // Map stays visible on top; scrollable content starts below it.
    val topSpacerHeight = (screenHeight * 0.55f).coerceAtLeast(400.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item(contentType = "Spacer") {
            Spacer(modifier = Modifier.height(topSpacerHeight))
        }

        item(contentType = "CertificateBanner") {
            CertificateBanner(
                state = state.certificateState,
                onRefresh = onRefreshCert,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (state.isBatteryOptimized) {
            item(contentType = "BatteryOptimization") {
                BatteryOptimizationBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        state.connectionWarning?.let { warning ->
            item(contentType = "ConnectionWarning") {
                ConnectionWarningBanner(
                    warning = warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (state.pauseEndTime > System.currentTimeMillis()) {
            item(contentType = "PauseBanner") {
                PauseBanner(
                    endTime = state.pauseEndTime,
                    onResume = onResume,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Current server + default-connection selector + connect button.
        item(contentType = "ConnectionStatus") {
            ConnectionStatusCard(
                isConnected = state.isConnected,
                isConnecting = state.isConnecting,
                originalLocationText = state.originalLocationText,
                vpnLocationText = state.vpnLocationText,
                isIpHidden = state.isIpHidden,
                quickConnectStrategy = state.quickConnectStrategy,
                quickConnectTargetId = state.quickConnectTargetId,
                profiles = state.profiles.toImmutableList(),
                onToggleIpVisibility = onToggleIpVisibility,
                onToggleConnection = {
                    if (state.isConnected || state.isConnecting) onDisconnect() else onQuickConnect()
                },
                onPause = onPause,
                onChangeQuickConnect = onChangeQuickConnect,
                vpnState = state.vpnState,
                connectedServer = state.connectedServer,
                allServers = state.servers.toImmutableList()
            )
        }

        // Recent connections (desktop-style card) between connection and stats.
        item(contentType = "RecentConnections") {
            RecentConnectionsCard(
                recents = state.recentConnections.toImmutableList(),
                connectedServerId = state.connectedServer?.id,
                onServerClick = onServerClick,
                maxVisible = 3,
                onShowAll = { showRecentsSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (state.netShieldLevel.enabled) {
            item(contentType = "NetShieldStats") {
                NetShieldStatsCard(
                    stats = state.netShieldStats,
                    level = state.netShieldLevel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Statistics card (port of the desktop stats slider).
        item(contentType = "StatsCard") {
            StatsCard(
                stats = stats,
                isConnected = state.isConnected,
                liveSpeed = state.speed,
                onToggle = onToggleStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(240.dp)
            )
        }
    }

    if (showRecentsSheet) {
        RecentConnectionsBottomSheet(
            recents = state.recentConnections.toImmutableList(),
            connectedServerId = state.connectedServer?.id,
            onServerClick = { server ->
                showRecentsSheet = false
                onServerClick(server)
            },
            onDismiss = { showRecentsSheet = false }
        )
    }
}

/**
 * Desktop-style "Recent connections" card: flag + country + city/server rows
 * with a chevron, inside a liquid-glass container.
 *
 * When [maxVisible] is set the card shows at most that many rows without any
 * inner scrolling, and [onShowAll] adds a small top-right button that opens
 * the full scrollable list (used on phones to avoid nested-scroll conflicts).
 */
@Composable
internal fun RecentConnectionsCard(
    recents: ImmutableList<LogicalServer>,
    connectedServerId: String?,
    onServerClick: (LogicalServer) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int? = null,
    onShowAll: (() -> Unit)? = null,
) {
    val colors = ProtonNextTheme.colors

    Column(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_recent_connections).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = colors.textWeak,
                modifier = Modifier.weight(1f)
            )
            if (onShowAll != null && recents.isNotEmpty()) {
                IconButton(
                    onClick = onShowAll,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = ProtonIcons.ThreeDotsHorizontal,
                        contentDescription = stringResource(R.string.title_recent_connections),
                        tint = colors.iconWeak,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        when {
            recents.isEmpty() -> {
                Box(
                    modifier = if (maxVisible == null) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_no_recents),
                        fontSize = 13.sp,
                        color = colors.textWeak
                    )
                }
            }
            maxVisible != null -> {
                // Static rows, no inner scrolling (phone).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recents.take(maxVisible).forEach { server ->
                        RecentServerRow(
                            server = server,
                            isActive = connectedServerId == server.id,
                            onClick = { onServerClick(server) }
                        )
                    }
                }
            }
            else -> {
                // Scrollable full list (tablet, bounded height).
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recents, key = { it.id }, contentType = { "RecentRow" }) { server ->
                        RecentServerRow(
                            server = server,
                            isActive = connectedServerId == server.id,
                            onClick = { onServerClick(server) }
                        )
                    }
                }
            }
        }
    }
}

/** Full scrollable list of recent connections (opened from the phone card). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentConnectionsBottomSheet(
    recents: ImmutableList<LogicalServer>,
    connectedServerId: String?,
    onServerClick: (LogicalServer) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ProtonNextTheme.colors
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.backgroundSecondary.copy(alpha = 0.95f),
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        // High opacity background ensures content is legible even without window-level blur.
        Text(
            text = stringResource(R.string.title_recent_connections).uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = colors.textWeak,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(recents, key = { it.id }, contentType = { "RecentRow" }) { server ->
                RecentServerRow(
                    server = server,
                    isActive = connectedServerId == server.id,
                    onClick = { onServerClick(server) }
                )
            }
        }
    }
}

/** One desktop-style recent-connection row. */
@Composable
private fun RecentServerRow(
    server: LogicalServer,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) colors.brandNorm.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagIcon(
            countryFlag = CountryUtils.getFlagResource(context, server.exitCountry),
            size = DpSize(28.dp, 20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = CountryUtils.getCountryName(context, server.exitCountry),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textNorm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(server.localizedCity ?: server.city, server.name)
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
                fontSize = 11.sp,
                color = colors.textWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = ProtonIcons.ChevronRight,
            contentDescription = null,
            tint = colors.iconWeak,
            modifier = Modifier.size(16.dp)
        )
    }
}
