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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme

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

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("DashboardScreen", "VPN permission granted")
            pendingServer?.let {
                viewModel.toggleConnection(it)
                pendingServer = null
            }
        } else {
            pendingServer = null
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
            HomeMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clickable { onNavigateToMap?.invoke() },
                connectedServer = (uiState as? DashboardUiState.Success)?.connectedServer,
                isConnecting = (uiState as? DashboardUiState.Success)?.isConnecting ?: false
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
                            onDisconnect = { viewModel.disconnect() }
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
    onDisconnect: () -> Unit
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
            ConnectionStatusCard(
                isConnected = state.isConnected,
                isConnecting = state.isConnecting,
                connectedServer = state.connectedServer,
                onToggleConnection = {
                    if (state.isConnected) {
                        onDisconnect()
                    } else {
                        state.servers.firstOrNull()?.let { onServerClick(it) }
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
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedServer: LogicalServer?,
    onToggleConnection: () -> Unit
) {
    val colors = ProtonNextTheme.colors
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.backgroundNorm),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.desc_country),
                        tint = colors.iconNorm
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected || isConnecting) "${connectedServer?.name}" else stringResource(R.string.label_fastest_server),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = if (isConnected || isConnecting) "${connectedServer?.city}, ${connectedServer?.exitCountry}" else stringResource(R.string.label_select_location),
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.backgroundNorm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = stringResource(R.string.desc_country),
                    tint = colors.iconNorm
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm
                )
                Text(
                    text = "${server.city}, ${server.exitCountry}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak
                )
            }
        }
    }
}
