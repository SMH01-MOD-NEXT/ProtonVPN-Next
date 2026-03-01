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

import android.app.Activity
import android.net.VpnService
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.vpn.AmneziaVpnManager

@Composable
fun DashboardScreen(
    onNavigateToMap: (() -> Unit)? = null,
    onNavigateToCountries: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToProfiles: (() -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingServer by remember { mutableStateOf<LogicalServer?>(null) }
    var isQuickConnectPending by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("DashboardScreen", "VPN permission granted")
            if (isQuickConnectPending) {
                viewModel.quickConnect()
                isQuickConnectPending = false
            } else {
                pendingServer?.let {
                    viewModel.toggleConnection(it)
                    pendingServer = null
                }
            }
        } else {
            pendingServer = null
            isQuickConnectPending = false
        }
    }

    val checkVpnAndConnect: (LogicalServer) -> Unit = { server ->
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingServer = server
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.toggleConnection(server)
            }
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(context, context.getString(R.string.error_system_appops), android.widget.Toast.LENGTH_LONG).show()
            viewModel.toggleConnection(server)
        }
    }

    val checkVpnAndQuickConnect: () -> Unit = {
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                isQuickConnectPending = true
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.quickConnect()
            }
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(context, context.getString(R.string.error_system_appops), android.widget.Toast.LENGTH_LONG).show()
            viewModel.quickConnect()
        }
    }

    val currentTarget = MainTarget.Home

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.backgroundNorm)
        ) {
            val successState = uiState as? DashboardUiState.Success
            HomeMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clickable { onNavigateToMap?.invoke() },
                allServers = successState?.servers ?: emptyList(),
                connectedServer = successState?.connectedServer,
                isConnecting = successState?.isConnecting ?: false,
                isInteractive = false
            )

            AnimatedContent(
                targetState = uiState,
                label = "dashboard_state",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandNorm)
                        }
                    }
                    is DashboardUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = colors.notificationError)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.loadServers() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.interactionNorm)
                                ) {
                                    Text(stringResource(R.string.btn_retry), color = colors.textInverted)
                                }
                            }
                        }
                    }
                    is DashboardUiState.Success -> {
                        DashboardContent(
                            state = state,
                            onServerClick = { server ->
                                checkVpnAndConnect(server)
                            },
                            onQuickConnect = {
                                checkVpnAndQuickConnect()
                            },
                            onDisconnect = { viewModel.disconnect() },
                            onRefreshCert = { viewModel.refreshCertificate() }
                        )
                    }
                }
            }

            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                showCountries = true,
                showGateways = false,
                navigateTo = { target ->
                    when (target) {
                        MainTarget.Countries -> onNavigateToCountries?.invoke()
                        MainTarget.Settings -> onNavigateToSettings?.invoke()
                        MainTarget.Profiles -> onNavigateToProfiles?.invoke()
                        else -> {}
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    onServerClick: (LogicalServer) -> Unit,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshCert: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = 120.dp
        )
    ) {
        item {
            Spacer(modifier = Modifier.height(380.dp))
        }

        item {
            CertificateBanner(
                state = state.certificateState,
                onRefresh = onRefreshCert,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            ConnectionStatusCard(
                isConnected = state.isConnected,
                isConnecting = state.isConnecting,
                connectedServer = state.connectedServer,
                onToggleConnection = {
                    if (state.isConnected) {
                        onDisconnect()
                    } else {
                        onQuickConnect()
                    }
                }
            )
        }

        if (state.recentConnections.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.backgroundNorm
                                )
                            )
                        )
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_recent_connections),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }

            items(state.recentConnections) { server ->
                Box(modifier = Modifier.background(colors.backgroundNorm)) {
                    ServerCard(
                        server = server,
                        isConnected = state.connectedServer?.id == server.id,
                        isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                        onClick = { onServerClick(server) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CertificateBanner(
    state: AmneziaVpnManager.CertificateState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == AmneziaVpnManager.CertificateState.Valid) return

    val colors = ProtonNextTheme.colors
    val (backgroundColor, contentColor, icon, message) = when (state) {
        is AmneziaVpnManager.CertificateState.ExpiringSoon -> Quadruple(
            colors.notificationWarning.copy(alpha = 0.1f),
            colors.notificationWarning,
            Icons.Rounded.Warning,
            stringResource(R.string.cert_msg_expiring_soon, state.hoursRemaining)
        )
        is AmneziaVpnManager.CertificateState.Expired -> Quadruple(
            colors.notificationError.copy(alpha = 0.1f),
            colors.notificationError,
            Icons.Default.ErrorOutline,
            stringResource(R.string.cert_msg_expired)
        )
        is AmneziaVpnManager.CertificateState.Refreshing -> Quadruple(
            colors.backgroundSecondary,
            colors.textNorm,
            Icons.Default.Refresh,
            stringResource(R.string.cert_msg_refreshing)
        )
        is AmneziaVpnManager.CertificateState.RefreshFailed -> {
            val msg = if (state.isFullyExpired) {
                stringResource(R.string.cert_msg_refresh_failed, state.error)
            } else {
                stringResource(R.string.cert_msg_auto_refresh_failed)
            }
            Quadruple(
                colors.notificationError.copy(alpha = 0.1f),
                colors.notificationError,
                Icons.Default.ErrorOutline,
                msg
            )
        }
        else -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            if (state is AmneziaVpnManager.CertificateState.Expired || state is AmneziaVpnManager.CertificateState.RefreshFailed) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.cert_btn_refresh_now), color = contentColor)
                }
            } else if (state is AmneziaVpnManager.CertificateState.Refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedServer: LogicalServer?,
    onToggleConnection: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val cardContainerColor = when {
        isConnected -> colors.notificationSuccess.copy(alpha = 0.15f)
        isConnecting -> colors.backgroundSecondary
        else -> colors.backgroundSecondary.copy(alpha = 0.5f)
    }

    val contentColor = colors.textNorm

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = when {
                    isConnected -> stringResource(R.string.status_connected)
                    isConnecting -> stringResource(R.string.status_connecting)
                    else -> stringResource(R.string.status_not_connected)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) colors.notificationSuccess else contentColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isConnecting) { /* TODO: Open Change Server Bottom Sheet */ }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected || isConnecting) {
                    val countryCode = connectedServer?.exitCountry
                    val flagResId = CountryUtils.getFlagResource(context, countryCode)
                    if (flagResId != 0) {
                        FlagIcon(
                            countryFlag = flagResId,
                            size = DpSize(48.dp, 32.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    FlagIcon(
                        countryFlag = R.drawable.flag_fastest,
                        size = DpSize(48.dp, 32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val countryName = connectedServer?.let { CountryUtils.getCountryName(context, it.exitCountry) }
                    
                    Text(
                        text = if (isConnected || isConnecting) {
                            "$countryName, ${connectedServer?.city}"
                        } else stringResource(R.string.label_fastest_server),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isConnected || isConnecting) {
                            connectedServer?.name ?: ""
                        } else stringResource(R.string.label_select_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.desc_change_server),
                    tint = colors.iconWeak.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onToggleConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) colors.shade20 else colors.brandNorm,
                    contentColor = if (isConnected) colors.textNorm else colors.textInverted
                ),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.textInverted,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isConnected) stringResource(R.string.btn_disconnect) else stringResource(R.string.btn_quick_connect),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ServerCard(
    server: LogicalServer,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundSecondary.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val flagResId = CountryUtils.getFlagResource(context, server.exitCountry)
            if (flagResId != 0) {
                FlagIcon(
                    countryFlag = flagResId,
                    size = DpSize(36.dp, 24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp, 24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.backgroundNorm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.desc_country),
                        tint = colors.iconNorm,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val countryName = CountryUtils.getCountryName(context, server.exitCountry)
                Text(
                    text = "$countryName, ${server.city}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm
                )
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak
                )
            }
        }
    }
}
