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

package ru.protonmod.next.ui.screens.profiles

import android.app.Activity
import android.net.VpnService
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VpnKey
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToCountries: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCreateNewProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val currentTarget = MainTarget.Profiles
    val context = LocalContext.current

    // Collect profiles from ViewModel
    val profiles by viewModel.profiles.collectAsState()

    // VPN Permission Launcher
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("ProfilesScreen", "VPN permission granted")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
        }
    }

    val checkVpnAndConnect: (() -> Unit) -> Unit = { connectAction ->
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingAction = connectAction
                vpnPermissionLauncher.launch(intent)
            } else {
                connectAction()
            }
        } catch (_: SecurityException) {
            // Fallback if AppOps permission is missing, proceed anyway
            connectAction()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewProfile,
                containerColor = colors.brandNorm,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 80.dp) // Avoid overlapping with BottomBar
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.desc_create_profile))
            }
        },
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.2f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.profiles_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                    )
                }

                if (profiles.isEmpty()) {
                    item {
                        EmptyProfilesState(modifier = Modifier.fillParentMaxSize(0.6f))
                    }
                } else {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCardItem(
                            profile = profile,
                            onConnect = {
                                checkVpnAndConnect {
                                    viewModel.connectWithProfile(profile)
                                    onNavigateToHome()
                                }
                            },
                            onEdit = { onEditProfile(profile.id) }
                        )
                    }
                }
            }

            // Bottom Navigation
            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                showCountries = true,
                showGateways = false,
                navigateTo = { target ->
                    when (target) {
                        MainTarget.Home -> onNavigateToHome()
                        MainTarget.Countries -> onNavigateToCountries()
                        MainTarget.Profiles -> { /* Already here */ }
                        MainTarget.Settings -> onNavigateToSettings()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCardItem(
    profile: VpnProfileUiModel,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    Card(
        onClick = onConnect,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Icon / Target Indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brandNorm.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    profile.targetCountry != null -> {
                        Text(
                            text = CountryUtils.getFlagForCountry(profile.targetCountry),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = colors.brandNorm,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Profile Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle with protocol, port, and target info
                val portStr = if (profile.port == 0) stringResource(R.string.settings_port_auto) else profile.port.toString()
                val targetName = when {
                    profile.targetServerId != null -> "Server: ${profile.targetServerId}"
                    profile.targetCity != null -> "🏙️ ${profile.targetCity}, ${CountryUtils.getCountryName(context, profile.targetCountry!!)}"
                    profile.targetCountry != null -> "${CountryUtils.getFlagForCountry(profile.targetCountry)} ${CountryUtils.getCountryName(context, profile.targetCountry)}"
                    else -> "⚡ ${stringResource(R.string.location_fastest)}"
                }

                Text(
                    text = "${profile.protocol} • $portStr • $targetName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak
                )

                // Show indicators if special features are enabled
                if (profile.isObfuscationEnabled || !profile.autoOpenUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.isObfuscationEnabled) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_obfuscation))
                        }
                        if (!profile.autoOpenUrl.isNullOrEmpty()) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_connect_go))
                        }
                    }
                }
            }

            // Edit Profile Button
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.desc_edit_profile),
                    tint = colors.iconWeak
                )
            }
        }
    }
}

@Composable
fun FeatureBadge(text: String) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.brandNorm.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.brandNorm
        )
    }
}

@Composable
fun EmptyProfilesState(modifier: Modifier = Modifier) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.VpnKey,
            contentDescription = null,
            tint = colors.iconWeak.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textWeak
        )
        Text(
            text = stringResource(R.string.profiles_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}
