package ru.protonmod.next.ui.screens.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.data.network.LogicalServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accessToken: String,
    sessionId: String,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Загружаем серверы при старте экрана
    LaunchedEffect(Unit) {
        viewModel.loadServers(accessToken, sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proton VPN-Next", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                                Text("Ошибка загрузки серверов", color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadServers(accessToken, sessionId) }) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                    is DashboardUiState.Success -> {
                        DashboardContent(
                            state = state,
                            onServerClick = { server -> viewModel.toggleConnection(server) }
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
        // Main Connection Card
        ConnectionStatusCard(
            isConnected = state.isConnected,
            connectedServer = state.connectedServer,
            onQuickConnect = {
                // Quick connect to the first available server
                state.servers.firstOrNull()?.let { onServerClick(it) }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Свободные серверы",
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
                    onClick = { onServerClick(server) }
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    connectedServer: LogicalServer?,
    onQuickConnect: () -> Unit
) {
    val cardColor = if (isConnected) Color(0xFF3DDC84) else MaterialTheme.colorScheme.surfaceVariant
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
                text = if (isConnected) "Подключено" else "Отключено",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected) "${connectedServer?.name} (${connectedServer?.city})" else "Ваш трафик не защищен",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Power Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                    .clickable { onQuickConnect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle Connection",
                    modifier = Modifier.size(40.dp),
                    tint = if (isConnected) Color.DarkGray else Color.White
                )
            }
        }
    }
}

@Composable
fun ServerCard(
    server: LogicalServer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                    contentDescription = "Country",
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

            // Server load or ping indicator can go here
        }
    }
}