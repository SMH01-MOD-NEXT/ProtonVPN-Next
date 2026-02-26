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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget

@Composable
fun DashboardScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToCountries: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToProfiles: (() -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("DashboardScreen", "VPN permission granted")
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            MapBackgroundPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clickable { onNavigateToMap() }
            )

            AnimatedContent(
                targetState = uiState,
                label = "dashboard_state",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is DashboardUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadServers() }) {
                                    Text(stringResource(R.string.btn_retry))
                                }
                            }
                        }
                    }
                    is DashboardUiState.Success -> {
                        DashboardContent(
                            state = state,
                            onServerClick = { server ->
                                try {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPermissionLauncher.launch(intent)
                                    } else {
                                        viewModel.toggleConnection(server)
                                    }
                                } catch (_: SecurityException) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.error_system_appops), android.widget.Toast.LENGTH_LONG).show()
                                    viewModel.toggleConnection(server)
                                }
                            }
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
    onServerClick: (LogicalServer) -> Unit
) {
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
                onQuickConnect = {
                    state.servers.firstOrNull()?.let { onServerClick(it) }
                }
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(top = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_recent_connections),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        items(if (state.recentConnections.isNotEmpty()) state.recentConnections else state.servers) { server ->
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
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

@Composable
fun MapBackgroundPlaceholder(modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    backgroundColor
                ),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY
            )
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = stringResource(R.string.map_placeholder),
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 80.dp)
        )
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedServer: LogicalServer?,
    onQuickConnect: () -> Unit
) {
    val cardContainerColor = when {
        isConnected -> Color(0xFF3DDC84).copy(alpha = 0.15f)
        isConnecting -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = MaterialTheme.colorScheme.onSurface

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
                color = if (isConnected) Color(0xFF3DDC84) else contentColor.copy(alpha = 0.7f),
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
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.desc_country),
                        tint = contentColor
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
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.desc_change_server),
                    tint = contentColor.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onQuickConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isConnected) contentColor else MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = stringResource(R.string.desc_country),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${server.city}, ${server.exitCountry}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun DashboardScreenPreview() {
    val mockServer = LogicalServer(
        id = "1",
        name = "CH-US 1",
        tier = 0,
        features = 0,
        entryCountry = "Switzerland",
        exitCountry = "Switzerland",
        city = "Zurich",
        servers = emptyList()
    )

    val mockState = DashboardUiState.Success(
        servers = listOf(
            mockServer,
            LogicalServer(
                id = "2",
                name = "NL-FREE 2",
                tier = 0,
                features = 0,
                entryCountry = "Netherlands",
                exitCountry = "Netherlands",
                city = "Amsterdam",
                servers = emptyList()
            )
        ),
        isConnected = false,
        isConnecting = false,
        connectedServer = null
    )

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MapBackgroundPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            )

            DashboardContent(
                state = mockState,
                onServerClick = {}
            )

            LiquidGlassBottomBar(
                selectedTarget = MainTarget.Home,
                showCountries = true,
                showGateways = true,
                navigateTo = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
