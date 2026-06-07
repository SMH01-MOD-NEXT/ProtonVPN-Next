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
import android.text.BidiFormatter
import ru.protonmod.next.utils.system.SystemUtils
import ru.protonmod.next.utils.ProtonLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.theme.ProtonColors
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet
import ru.protonmod.next.vpn.AmneziaVpnManager

// --- Extensions for UI Effects matching Original Proton ---

@Composable
fun Modifier.vpnStatusOverlayBackground(
    isConnected: Boolean,
    isConnecting: Boolean,
    colors: ProtonColors
): Modifier {
    val targetColor = when {
        isConnected -> colors.notificationSuccess.copy(alpha = 0.4f)
        isConnecting -> Color.White.copy(alpha = 0.4f)
        else -> colors.notificationError.copy(alpha = 0.4f)
    }

    val gradientColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "Gradient Animation"
    )

    return this.background(
        Brush.verticalGradient(
            colors = listOf(gradientColor, gradientColor.copy(alpha = 0.0F))
        )
    )
}

@Composable
fun VpnStatusTop(
    isConnected: Boolean,
    isConnecting: Boolean,
    vpnState: AmneziaVpnManager.VpnState,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = vpnState,
            label = "VpnStatusTopTransition"
        ) { state ->
            when (state) {
                AmneziaVpnManager.VpnState.CONNECTED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_proton_lock_filled),
                            tint = colors.notificationSuccess,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.status_connected),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.notificationSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                AmneziaVpnManager.VpnState.CONNECTING, AmneziaVpnManager.VpnState.VERIFYING -> {
                    ExpressiveCircularProgressIndicator(
                        color = colors.iconNorm,
                        modifier = Modifier.size(32.dp)
                    )
                }
                else -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_proton_lock_open_filled_2),
                        contentDescription = null,
                        tint = colors.notificationError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// --- Masked Location Text Components ---

private fun annotatedCountryHighlight(
    text: String,
    highlight: String,
    colors: ProtonColors,
    displayText: String = text,
) = buildAnnotatedString {
    append(displayText)
    val startIndex = text.indexOf(highlight)
    if (startIndex >= 0) {
        val styleStart = startIndex.coerceAtMost(displayText.length)
        val styleEnd = (startIndex + highlight.length).coerceAtMost(displayText.length)
        if (styleStart < styleEnd) {
            addStyle(
                style = SpanStyle(color = colors.textNorm, fontWeight = FontWeight.SemiBold),
                start = styleStart,
                end = styleEnd
            )
        }
    }
}

/**
 * Text that can beautifully obscure its contents with a character-by-character animation.
 * Replaces chars with '*' while keeping spaces and dots intact.
 */
@Composable
private fun ObscurableText(
    targetText: String,
    highlightText: String,
    isObscured: Boolean,
    modifier: Modifier = Modifier,
    duration: Int = 30, // Animation speed per character
    targetCharacter: Char = '*',
    preserveCharacters: CharArray = charArrayOf('.', ' ', '-', ':')
) {
    var displayText by remember {
        mutableStateOf(
            if (isObscured) {
                val chars = targetText.toCharArray()
                for (i in chars.indices) {
                    if (!preserveCharacters.contains(chars[i])) chars[i] = targetCharacter
                }
                String(chars)
            } else {
                targetText
            }
        )
    }

    var fixedWidth by remember { mutableStateOf<Int?>(null) }
    // Track the previous target string to rebuild the base perfectly when the IP changes
    var previousTargetText by remember { mutableStateOf(targetText) }

    Box(modifier = modifier) {
        val indicesToAnimate = remember(isObscured, targetText) {
            targetText.indices
                .filter { !preserveCharacters.contains(targetText[it]) }
                .shuffled()
        }

        LaunchedEffect(isObscured, targetText) {
            val targetChars = targetText.toCharArray()
            var currentChars = displayText.toCharArray()

            // Check if the underlying string itself has changed (e.g., completely new IP loaded)
            val baseChanged = previousTargetText != targetText || currentChars.size != targetChars.size

            if (baseChanged) {
                fixedWidth = null
                previousTargetText = targetText

                val baseChars = targetChars.clone()
                if (isObscured) {
                    for (i in baseChars.indices) {
                        if (!preserveCharacters.contains(baseChars[i])) baseChars[i] = targetCharacter
                    }
                }
                // Update displayText immediately for base change to align characters
                displayText = String(baseChars)
                currentChars = baseChars
            }

            // Always run the animation loop to ensure state matches targetText/isObscured
            for (i in indicesToAnimate) {
                if (isObscured && currentChars[i] == targetCharacter) continue
                if (!isObscured && currentChars[i] == targetChars[i]) continue

                delay(duration.toLong())
                val newChar = if (isObscured) targetCharacter else targetChars[i]
                currentChars[i] = newChar
                displayText = String(currentChars)
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val colors = ProtonNextTheme.colors
            Layout(
                content = {
                    Text(
                        text = annotatedCountryHighlight(
                            text = targetText,
                            highlight = highlightText,
                            colors = colors,
                            displayText = displayText
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProtonNextTheme.colors.textWeak,
                        modifier = Modifier.onGloballyPositioned {
                            // Prevent layout jumping while animating asterisks
                            if (fixedWidth == null || fixedWidth!! < it.size.width) {
                                fixedWidth = it.size.width
                            }
                        },
                    )
                },
                measurePolicy = { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    val width = fixedWidth ?: placeable.width
                    val offsetX = (width - placeable.width) / 2
                    layout(width, placeable.height) {
                        placeable.placeRelative(offsetX, 0)
                    }
                }
            )
        }
    }
}

@Composable
private fun LocationTextElement(
    locationText: LocationText,
    isObscured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(modifier = modifier) {
        Surface(
            color = colors.backgroundSecondary.copy(alpha = 0.86F),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(listOf(colors.shade100.copy(alpha = 0.08f), colors.shade100.copy(alpha = 0.02f)))
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick) // Makes the entire IP block clickable to toggle privacy mode
        ) {
            val unknown = stringResource(R.string.unknown)
            
            // Data is now sanitized at the Mapper level, so we only need simple fallbacks
            val safeIp = locationText.ip.ifBlank { unknown }
            
            val safeCountry = if (locationText.ip.isBlank()) {
                unknown
            } else {
                locationText.country.ifBlank { stringResource(R.string.status_not_connected) }
            }

            val country = BidiFormatter.getInstance().unicodeWrap(safeCountry)
            val fullText = stringResource(R.string.location_format, country, safeIp)

            ObscurableText(
                targetText = fullText,
                highlightText = country,
                isObscured = isObscured,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

// --- Main Screen ---

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingServer by remember { mutableStateOf<LogicalServer?>(null) }
    var isQuickConnectPending by remember { mutableStateOf(false) }
    val isTablet = isTablet()

    var showQuickConnectConfig by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("DashboardScreen", "VPN permission granted")
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

    var showPauseDialog by remember { mutableStateOf(false) }

    val errorAppOpsMsg = stringResource(R.string.error_system_appops)
    val errorVpnDialogNotFound = stringResource(R.string.error_vpn_permission_dialog_not_found)

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
            android.widget.Toast.makeText(context, errorAppOpsMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.toggleConnection(server)
        } catch (_: android.content.ActivityNotFoundException) {
            pendingServer = null
            android.widget.Toast.makeText(context, errorVpnDialogNotFound, android.widget.Toast.LENGTH_LONG).show()
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
            android.widget.Toast.makeText(context, errorAppOpsMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.quickConnect()
        } catch (_: android.content.ActivityNotFoundException) {
            isQuickConnectPending = false
            android.widget.Toast.makeText(context, errorVpnDialogNotFound, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.backgroundNorm,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val successState = uiState as? DashboardUiState.Success
            val isConnected = successState?.isConnected == true
            val isConnecting = successState?.isConnecting == true

            // Background gradient decoration (immersive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.backgroundNorm)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isTablet) 1f else 0.6f)
            ) {
                HomeMap(
                    allServers = (successState?.servers ?: emptyList()).toImmutableList(),
                    connectedServer = successState?.connectedServer,
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    modifier = Modifier.fillMaxSize(),
                    userCountryCode = successState?.originalLocationText?.countryCode,
                    isInteractive = isTablet
                )

                // Fade out map at the bottom to blend with background
                if (!isTablet) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, colors.backgroundNorm)
                                )
                            )
                    )
                }
            }

            if (!isTablet) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.TopCenter)
                        .vpnStatusOverlayBackground(isConnected, isConnecting, colors)
                )
            }

            VpnStatusTop(
                isConnected = isConnected,
                isConnecting = isConnecting,
                vpnState = successState?.vpnState ?: AmneziaVpnManager.VpnState.DISCONNECTED,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp)
            )

            val baseState = when (uiState) {
                is DashboardUiState.Loading -> 0
                is DashboardUiState.Error -> 1
                is DashboardUiState.Success -> 2
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = baseState,
                    label = "dashboard_state",
                    modifier = Modifier.fillMaxSize()
                ) { target ->
                    when (target) {
                        0 -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ExpressiveCircularProgressIndicator(color = colors.brandNorm)
                            }
                        }
                        1 -> {
                            val errorState = uiState as? DashboardUiState.Error
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(errorState?.message.orEmpty(), color = colors.notificationError)
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
                        2 -> {
                            val state = uiState as? DashboardUiState.Success
                            if (state != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    DashboardContent(
                                        state = state,
                                        isTablet = isTablet,
                                        onServerClick = { server -> checkVpnAndConnect(server) },
                                        onQuickConnect = { checkVpnAndQuickConnect() },
                                        onDisconnect = { viewModel.disconnect() },
                                        onPause = { showPauseDialog = true },
                                        onResume = { viewModel.resumeVpn() },
                                        onRefreshCert = { viewModel.refreshCertificate() },
                                        onToggleIpVisibility = { viewModel.toggleIpVisibility() },
                                        onChangeQuickConnect = { showQuickConnectConfig = true }
                                    )
                                }

                                if (showPauseDialog) {
                                    PauseDialog(
                                        onDismiss = { showPauseDialog = false },
                                        onPause = { durationMs ->
                                            viewModel.pauseVpn(durationMs)
                                            showPauseDialog = false
                                        }
                                    )
                                }

                                if (showQuickConnectConfig) {
                                    QuickConnectBottomSheet(
                                        onDismiss = { showQuickConnectConfig = false },
                                        currentStrategy = state.quickConnectStrategy,
                                        currentTargetId = state.quickConnectTargetId,
                                        profiles = state.profiles.toImmutableList(),
                                        recentServers = state.recentConnections.toImmutableList(),
                                        onStrategySelect = { strategy, targetId ->
                                            viewModel.setQuickConnectStrategy(strategy, targetId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    onServerClick: (LogicalServer) -> Unit,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefreshCert: () -> Unit,
    onToggleIpVisibility: () -> Unit,
    onChangeQuickConnect: () -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false
) {
    val colors = ProtonNextTheme.colors
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
    
    Box(modifier = modifier) {
        if (isTablet) {
            // Tablet Layout: Split connection (Left) and recent connections (Right)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 140.dp), // Increased for the centered bottom bar
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Left Side: Connection Status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    CertificateBanner(
                        state = state.certificateState,
                        onRefresh = onRefreshCert,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (state.isBatteryOptimized) {
                        BatteryOptimizationBanner(modifier = Modifier.padding(bottom = 16.dp))
                    }

                    if (state.pauseEndTime > System.currentTimeMillis()) {
                        PauseBanner(
                            endTime = state.pauseEndTime,
                            onResume = onResume,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

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
                            if (state.isConnected) onDisconnect() else onQuickConnect()
                        },
                        onPause = onPause,
                        onChangeQuickConnect = onChangeQuickConnect,
                        vpnState = state.vpnState,
                        connectedServer = state.connectedServer,
                        allServers = state.servers.toImmutableList()
                    )

                    if (state.isConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                label = stringResource(R.string.label_speed),
                                value = state.speed ?: "0 B/s",
                                icon = Icons.Rounded.Speed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    label = stringResource(R.string.label_download),
                                    value = state.trafficRx ?: "0 B",
                                    icon = Icons.Rounded.CloudDownload,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = stringResource(R.string.label_upload),
                                    value = state.trafficTx ?: "0 B",
                                    icon = Icons.Rounded.CloudUpload,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Right Side: Recent Connections
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.recentConnections.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.title_recent_connections),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textNorm,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.backgroundNorm.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.recentConnections, key = { it.id }, contentType = { "Server" }) { server ->
                                ServerCard(
                                    server = server,
                                    isConnected = state.connectedServer?.id == server.id,
                                    isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                                    displayMode = state.serverLoadDisplayMode,
                                    onClick = { onServerClick(server) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Phone Layout (original LazyColumn)
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

                if (state.pauseEndTime > System.currentTimeMillis()) {
                    item(contentType = "PauseBanner") {
                        PauseBanner(
                            endTime = state.pauseEndTime,
                            onResume = onResume,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

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
                            if (state.isConnected) onDisconnect() else onQuickConnect()
                        },
                        onPause = onPause,
                        onChangeQuickConnect = onChangeQuickConnect,
                        vpnState = state.vpnState,
                        connectedServer = state.connectedServer,
                        allServers = state.servers.toImmutableList()
                    )

                    if (state.isConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                label = stringResource(R.string.label_speed),
                                value = state.speed ?: "0 B/s",
                                icon = Icons.Rounded.Speed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    label = stringResource(R.string.label_download),
                                    value = state.trafficRx ?: "0 B",
                                    icon = Icons.Rounded.CloudDownload,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = stringResource(R.string.label_upload),
                                    value = state.trafficTx ?: "0 B",
                                    icon = Icons.Rounded.CloudUpload,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (state.recentConnections.isNotEmpty()) {
                    item(contentType = "RecentConnectionsHeader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, colors.backgroundNorm)
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

                    items(state.recentConnections, key = { it.id }, contentType = { "Server" }) { server ->
                        Box(modifier = Modifier.background(colors.backgroundNorm)) {
                            ServerCard(
                                server = server,
                                isConnected = state.connectedServer?.id == server.id,
                                isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                                displayMode = state.serverLoadDisplayMode,
                                onClick = { onServerClick(server) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
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
        is AmneziaVpnManager.CertificateState.Error -> Quadruple(
            colors.notificationError.copy(alpha = 0.1f),
            colors.notificationError,
            Icons.Default.ErrorOutline,
            state.message
        )
    }

    val isRefreshing = state is AmneziaVpnManager.CertificateState.Refreshing
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_anim")
    val rotation by if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
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
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation }
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
            } else if (isRefreshing) {
                Text(
                    text = stringResource(R.string.cert_msg_refreshing),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
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
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = 0.4f,
                shadowElevation = 0.dp
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textWeak
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textNorm
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    originalLocationText: LocationText?,
    vpnLocationText: LocationText?,
    isIpHidden: Boolean,
    quickConnectStrategy: String,
    quickConnectTargetId: String?,
    profiles: ImmutableList<ru.protonmod.next.data.local.VpnProfileEntity>,
    onToggleIpVisibility: () -> Unit,
    onToggleConnection: () -> Unit,
    onPause: () -> Unit,
    onChangeQuickConnect: () -> Unit,
    modifier: Modifier = Modifier,
    vpnState: AmneziaVpnManager.VpnState = AmneziaVpnManager.VpnState.DISCONNECTED,
    connectedServer: LogicalServer? = null,
    allServers: ImmutableList<LogicalServer> = kotlinx.collections.immutable.persistentListOf()
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    val contentColor = colors.textNorm

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .liquidGlass(
                shape = RoundedCornerShape(32.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (vpnState) {
                        AmneziaVpnManager.VpnState.CONNECTED -> stringResource(R.string.status_connected)
                        AmneziaVpnManager.VpnState.CONNECTING -> stringResource(R.string.status_connecting)
                        AmneziaVpnManager.VpnState.VERIFYING -> stringResource(R.string.status_verifying)
                        AmneziaVpnManager.VpnState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                        else -> stringResource(R.string.status_not_connected)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) colors.notificationSuccess else contentColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                // Manage the intermediate state when the VPN is UP, but the real IP hasn't been fetched yet
                val isFetchingVpnIp = isConnected && vpnLocationText == null

                val currentLocation = when {
                    isConnected && vpnLocationText != null -> vpnLocationText
                    isConnected && vpnLocationText == null -> {
                        // Provide a dummy IP string while waiting for the real one.
                        val rawCountry = connectedServer?.exitCountry?.let { CountryUtils.getCountryName(context, it) }
                        val safeCountry = rawCountry?.ifBlank { null } ?: stringResource(R.string.unknown)
                        LocationText(country = safeCountry, countryCode = connectedServer?.exitCountry, ip = stringResource(R.string.unknown))
                    }
                    else -> originalLocationText ?: LocationText(country = stringResource(R.string.status_connecting), ip = stringResource(R.string.unknown))
                }

                Spacer(modifier = Modifier.width(12.dp))
                LocationTextElement(
                    locationText = currentLocation,
                    // Force obscuring when connecting, hiding IP manually, waiting for VPN IP, or waiting for Original IP
                    isObscured = isIpHidden || isConnecting || isFetchingVpnIp || originalLocationText == null,
                    onClick = onToggleIpVisibility
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isConnecting) { onChangeQuickConnect() }
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
                    val targetServer = if (quickConnectStrategy == "server") {
                        allServers.find { it.id == quickConnectTargetId }
                    } else null

                    val flagRes = when {
                        targetServer != null -> CountryUtils.getFlagResource(context, targetServer.exitCountry)
                        quickConnectStrategy == "fastest" || quickConnectStrategy == "recent" -> R.drawable.flag_fastest
                        else -> 0
                    }

                    if (flagRes != 0) {
                        FlagIcon(
                            countryFlag = flagRes,
                            size = DpSize(48.dp, 32.dp)
                        )
                    } else {
                        val iconVector = when (quickConnectStrategy) {
                            "profile" -> Icons.Rounded.Star
                            else -> Icons.Rounded.Speed
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp, 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = colors.brandNorm,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val rawCountry = connectedServer?.let { CountryUtils.getCountryName(context, it.exitCountry) }
                    val safeCountryName = rawCountry?.ifBlank { null } ?: stringResource(R.string.status_vpn)
                    val safeCityName = connectedServer?.city ?: ""

                    val targetServer = if (quickConnectStrategy == "server") {
                        allServers.find { it.id == quickConnectTargetId }
                    } else null

                    val locationTitleText = if (isConnected || isConnecting) {
                        if (safeCityName.isNotEmpty()) {
                            stringResource(R.string.location_city_format, safeCountryName, safeCityName)
                        } else {
                            safeCountryName
                        }
                    } else {
                        when (quickConnectStrategy) {
                            "fastest" -> stringResource(R.string.qc_strategy_fastest)
                            "recent" -> stringResource(R.string.qc_strategy_recent)
                            "profile" -> profiles.find { it.id == quickConnectTargetId }?.name ?: stringResource(R.string.label_fastest_server)
                            "server" -> targetServer?.let {
                                val cName = CountryUtils.getCountryName(context, it.exitCountry)
                                if (it.city.isNotBlank()) "$cName, ${it.city}" else cName
                            } ?: stringResource(R.string.label_fastest_server)
                            else -> stringResource(R.string.label_fastest_server)
                        }
                    }

                    Text(
                        text = locationTitleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isConnected || isConnecting) {
                            connectedServer?.name ?: ""
                        } else {
                            if (quickConnectStrategy == "server") targetServer?.name ?: ""
                            else stringResource(R.string.label_select_location)
                        },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, colors.shade20)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_pause),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textNorm
                        )
                    }
                }

                Button(
                    onClick = onToggleConnection,
                    modifier = Modifier
                        .weight(if (isConnected) 2f else 1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) colors.shade20 else colors.brandNorm,
                        contentColor = if (isConnected) colors.textNorm else colors.textInverted
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isConnected) 0.dp else 4.dp,
                        pressedElevation = 2.dp
                    ),
                    enabled = !isConnecting
                ) {
                    if (isConnecting) {
                        ExpressiveCircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.textInverted
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
}

@Composable
fun BatteryOptimizationBanner(
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.notificationWarning.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.notificationWarning,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.battery_optimization_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.notificationWarning,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.battery_optimization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.notificationWarning
                )
            }
            TextButton(
                onClick = {
                    SystemUtils.openBatteryOptimizationSettings(context)
                }
            ) {
                Text(stringResource(R.string.btn_fix), color = colors.notificationWarning)
            }
        }
    }
}

@Composable
fun PauseBanner(
    endTime: Long,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var timeLeft by remember(endTime) { 
        mutableLongStateOf((endTime - System.currentTimeMillis()).coerceAtLeast(0) / 1000)
    }

    Box(modifier = modifier) {
        LaunchedEffect(endTime) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft = (endTime - System.currentTimeMillis()).coerceAtLeast(0) / 1000
            }
        }

        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.brandNorm.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed, // Using Speed icon for Pause indicator
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pause_active_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.brandNorm,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pause_active_desc, timeStr),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.brandNorm
                    )
                }
                TextButton(onClick = onResume) {
                    Text(stringResource(R.string.btn_resume), color = colors.brandNorm)
                }
            }
        }
    }
}

@Composable
fun PauseDialog(
    onDismiss: () -> Unit,
    onPause: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var showCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pause_dialog_title), color = colors.textNorm) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showCustom) {
                    Text(stringResource(R.string.pause_dialog_desc), color = colors.textWeak)
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(5, 15, 60).forEach { minutes ->
                        Button(
                            onClick = { onPause(minutes * 60 * 1000L) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.backgroundSecondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.pause_option, minutes), color = colors.textNorm)
                        }
                    }
                    OutlinedButton(
                        onClick = { showCustom = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.shade20)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pause_custom), color = colors.textNorm)
                    }
                } else {
                    CustomPauseContent(onPause = onPause)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel), color = colors.brandNorm)
            }
        },
        containerColor = colors.backgroundNorm,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPauseContent(
    onPause: (Long) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var timeInput by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableIntStateOf(1) } // 0: Sec, 1: Min, 2: Hour
    var expanded by remember { mutableStateOf(false) }

    val units = listOf(
        stringResource(R.string.pause_unit_seconds),
        stringResource(R.string.pause_unit_minutes),
        stringResource(R.string.pause_unit_hours)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SmoothOutlinedTextField(
            value = timeInput,
            onValueChange = { if (it.all { char -> char.isDigit() }) timeInput = it },
            label = { Text(stringResource(R.string.label_speed)) }, // Reuse existing label or add new
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandNorm,
                unfocusedBorderColor = colors.shade20
            )
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = units[selectedUnit],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.brandNorm,
                    unfocusedBorderColor = colors.shade20
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.backgroundSecondary)
            ) {
                units.forEachIndexed { index, unit ->
                    DropdownMenuItem(
                        text = { Text(unit, color = colors.textNorm) },
                        onClick = {
                            selectedUnit = index
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val value = timeInput.toLongOrNull() ?: 0L
                val multiplier = when (selectedUnit) {
                    0 -> 1000L
                    1 -> 60 * 1000L
                    2 -> 60 * 60 * 1000L
                    else -> 1000L
                }
                if (value > 0) onPause(value * multiplier)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
            shape = RoundedCornerShape(12.dp),
            enabled = timeInput.isNotBlank()
        ) {
            Text(stringResource(R.string.btn_start_pause))
        }
    }
}

@Composable
fun ServerCard(
    server: LogicalServer,
    isConnected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
    displayMode: ru.protonmod.next.data.local.ServerLoadDisplayMode = ru.protonmod.next.data.local.ServerLoadDisplayMode.ALL,
    onClick: (() -> Unit)? = null,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = if (isConnected) 0.3f else 0.4f,
                shadowElevation = 0.dp
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = !isConnecting) { onClick() }
                } else {
                    Modifier
                }
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp, 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        ExpressiveCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.brandNorm
                        )
                    } else {
                        val flagResId = CountryUtils.getFlagResource(context, server.exitCountry)
                        if (flagResId != 0) {
                            FlagIcon(
                                countryFlag = flagResId,
                                size = DpSize(36.dp, 24.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val rawCountry = CountryUtils.getCountryName(context, server.exitCountry)
                    val safeCountry = rawCountry.ifBlank { stringResource(R.string.status_vpn) }
                    val safeCity = server.localizedCity ?: server.city
                    val locationTitle = if (safeCity.isNotEmpty()) {
                        stringResource(R.string.location_city_format, safeCountry, safeCity)
                    } else {
                        safeCountry
                    }

                    Text(
                        text = locationTitle,
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

                if (!isConnecting) {
                    ru.protonmod.next.ui.components.LoadIndicator(
                        load = server.averageLoad,
                        displayMode = displayMode
                    )
                }
            }

            ru.protonmod.next.ui.components.LoadProgressBar(
                load = server.averageLoad,
                displayMode = displayMode
            )
        }
    }
}
