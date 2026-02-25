package ru.protonmod.next.ui.screens.dashboard

import android.app.Activity
import android.net.VpnService
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launcher for Android's system VPN permission dialog
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted, state will be handled in ViewModel if retried
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadServers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.desc_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(targetState = uiState, label = "dashboard_state") { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is DashboardUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.error_loading_servers), color = MaterialTheme.colorScheme.error)
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
                                    // Проверяем разрешение VPN. Оборачиваем в try-catch для защиты от системных багов AppOps.
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPermissionLauncher.launch(intent)
                                    } else {
                                        viewModel.toggleConnection(server)
                                    }
                                } catch (e: SecurityException) {
                                    android.util.Log.e("DashboardScreen", "System AppOps Error", e)
                                    android.widget.Toast.makeText(context, context.getString(R.string.error_system_appops), android.widget.Toast.LENGTH_LONG).show()
                                    // Пытаемся запустить в обход, если система уже дала права, но глючит
                                    viewModel.toggleConnection(server)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    onServerClick: (LogicalServer) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ConnectionStatusCard(
            isConnected = state.isConnected,
            isConnecting = state.isConnecting,
            connectedServer = state.connectedServer,
            onQuickConnect = {
                state.servers.firstOrNull()?.let { onServerClick(it) }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.title_free_servers),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.servers) { server ->
                ServerCard(
                    server = server,
                    isConnected = state.connectedServer?.id == server.id,
                    isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                    onClick = { onServerClick(server) }
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedServer: LogicalServer?,
    onQuickConnect: () -> Unit
) {
    val cardColor = when {
        isConnected -> Color(0xFF3DDC84)
        isConnecting -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isConnected) Color.Black else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isConnected -> stringResource(R.string.status_connected)
                    isConnecting -> stringResource(R.string.status_connecting)
                    else -> stringResource(R.string.status_disconnected)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected || isConnecting) "${connectedServer?.name} (${connectedServer?.city})" else stringResource(R.string.msg_traffic_not_protected),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !isConnecting) { onQuickConnect() },
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = stringResource(R.string.desc_toggle_connection),
                        modifier = Modifier.size(40.dp),
                        tint = if (isConnected) Color.DarkGray else Color.White
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        shape = RoundedCornerShape(16.dp),
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