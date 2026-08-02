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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.MainHeader
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet
import ru.protonmod.next.utils.ProtonLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateToHome: () -> Unit,
    onCreateNewProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val isTablet = isTablet()

    // Collect profiles from ViewModel
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    // VPN Permission Launcher
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("ProfilesScreen", "VPN permission granted")
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
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!isTablet) {
                FloatingActionButton(
                    onClick = onCreateNewProfile,
                    containerColor = colors.brandNorm,
                    contentColor = colors.onInteraction,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 130.dp)
                ) {
                    Icon(ProtonIcons.Plus, contentDescription = stringResource(R.string.desc_create_profile))
                }
            }
        },
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration (immersive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.backgroundNorm.copy(alpha = 0.1f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                if (profiles.isEmpty()) {
                    // Header for empty state
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ProfilesHeader(
                            isTablet = isTablet,
                            onCreateNewProfile = onCreateNewProfile
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyProfilesState()
                    }
                } else if (isTablet) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 420.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                            ProfilesHeader(
                                isTablet = true,
                                onCreateNewProfile = onCreateNewProfile
                            )
                        }

                        items(profiles, key = { it.id }, contentType = { "Profile" }) { profile ->
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
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(contentType = "Header") {
                            ProfilesHeader(
                                isTablet = false,
                                onCreateNewProfile = onCreateNewProfile
                            )
                        }

                        items(profiles, key = { it.id }, contentType = { "Profile" }) { profile ->
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
            }
        }
    }
}

@Composable
private fun ProfilesHeader(
    isTablet: Boolean,
    onCreateNewProfile: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainHeader(
            title = stringResource(R.string.profiles_title),
            modifier = Modifier.weight(1f)
        )

        if (isTablet) {
            Button(
                onClick = onCreateNewProfile,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(end = 16.dp)
            ) {
                Icon(ProtonIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.desc_create_profile))
            }
        }
    }
}

@Composable
fun ProfileCardItem(
    profile: VpnProfileUiModel,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    // Accent gradient by connection target: fastest = green, city = red,
    // specific server = metallic gray, country-only = brand color.
    val accent = remember(profile.targetServerId, profile.targetCity, profile.targetCountry, colors.brandNorm) {
        getProfileAccent(profile.targetServerId, profile.targetCity, profile.targetCountry, colors.brandNorm)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(28.dp), alpha = 0.4f, shadowElevation = 0.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.start.copy(alpha = 0.22f),
                        Color.Transparent,
                        accent.end.copy(alpha = 0.14f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Icon / Target Indicator (1.5x scale: 72x48)
            Box(
                modifier = Modifier
                    .size(72.dp, 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.start.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    profile.targetCountry != null -> {
                        val flagResId = CountryUtils.getFlagResource(context, profile.targetCountry)
                        if (flagResId != 0) {
                            FlagIcon(
                                countryFlag = flagResId,
                                size = DpSize(72.dp, 48.dp)
                            )
                        } else {
                            Text(
                                text = CountryUtils.getFlagForCountry(profile.targetCountry),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    else -> {
                        FlagIcon(
                            countryFlag = R.drawable.flag_fastest,
                            size = DpSize(72.dp, 48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Profile Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle with protocol, port, and target info
                val portStr = if (profile.port == 0) stringResource(R.string.settings_port_auto) else profile.port.toString()
                val targetName = when {
                    profile.targetServerId != null -> stringResource(R.string.profile_server_info, profile.targetServerName ?: profile.targetServerId)
                    profile.targetCity != null -> {
                        val displayCity = profile.localizedCity ?: profile.targetCity
                        stringResource(R.string.profile_city_info, displayCity, CountryUtils.getCountryName(context, profile.targetCountry!!))
                    }
                    profile.targetCountry != null -> {
                        val flagEmoji = CountryUtils.getFlagForCountry(profile.targetCountry)
                        val localizedCountryName = CountryUtils.getCountryName(context, profile.targetCountry)
                        stringResource(R.string.profile_country_info, flagEmoji, localizedCountryName)
                    }
                    else -> stringResource(R.string.profile_fastest_info, stringResource(R.string.location_fastest))
                }

                Text(
                    text = stringResource(R.string.profile_info_format, profile.protocol, portStr, targetName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Show indicators if special features are enabled
                if (profile.isObfuscationEnabled || !profile.autoOpenUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.isObfuscationEnabled) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_obfuscation), accent = accent.start)
                        }
                        if (!profile.autoOpenUrl.isNullOrEmpty()) {
                            FeatureBadge(text = stringResource(R.string.profile_feature_connect_go), accent = accent.start)
                        }
                    }
                }
            }

            // Edit Profile Button
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = ProtonIcons.Pen,
                    contentDescription = stringResource(R.string.desc_edit_profile),
                    tint = colors.iconWeak,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun FeatureBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val badgeColor = accent ?: ProtonNextTheme.colors.brandNorm
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor
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
            imageVector = ProtonIcons.Key,
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
