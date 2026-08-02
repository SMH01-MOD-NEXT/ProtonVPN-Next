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

package ru.protonmod.next.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.LocalColors
import ru.protonmod.next.ui.theme.ProtonColors
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
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

            val themes = remember {
                AppTheme.entries.filter { theme ->
                    when (theme) {
                        AppTheme.SYSTEM -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        AppTheme.NOTHING -> ru.protonmod.next.utils.system.SystemUtils.isNothingDevice()
                        else -> true
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    contentType = "Header"
                ) {
                    NavigationHeader(
                        title = stringResource(R.string.settings_app_theme),
                        onBack = onBack
                    )
                }

                item(
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    contentType = "Header"
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Header Icon
                            Box(
                                modifier = Modifier.padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(colors.brandNorm.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ProtonIcons.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = colors.brandNorm
                                    )
                                }
                            }

                            // Title
                            Text(
                                text = stringResource(R.string.settings_app_theme),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textNorm,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Description
                            Text(
                                text = stringResource(R.string.settings_app_theme_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textWeak,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }

                    items(themes, key = { it.name }, contentType = { "Theme" }) { theme ->
                        ThemePreviewCard(
                            theme = theme,
                            isSelected = uiState.appTheme == theme,
                            onClick = { viewModel.setAppTheme(theme) },
                            modifier = Modifier.padding(
                                start = if (themes.indexOf(theme) % 2 == 0) 16.dp else 0.dp,
                                end = if (themes.indexOf(theme) % 2 == 1) 16.dp else 0.dp
                            )
                        )
                    }
                }
            }
        }
    }

@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val themeName = when (theme) {
        AppTheme.SYSTEM -> stringResource(R.string.theme_system)
        AppTheme.LIGHT -> stringResource(R.string.theme_light)
        AppTheme.DARK -> stringResource(R.string.theme_dark)
        AppTheme.AMOLED -> stringResource(R.string.theme_amoled)
        AppTheme.GOLD_LIGHT -> stringResource(R.string.theme_gold_light)
        AppTheme.GOLD_DARK -> stringResource(R.string.theme_gold_dark)
        AppTheme.GOLD_AMOLED -> stringResource(R.string.theme_gold_amoled)
        AppTheme.SURFSHARK -> stringResource(R.string.theme_surfshark)
        AppTheme.NORD -> stringResource(R.string.theme_nord)
        AppTheme.IPVANISH -> stringResource(R.string.theme_ipvanish)
        AppTheme.PUREVPN -> stringResource(R.string.theme_purevpn)
        AppTheme.MULLVAD -> stringResource(R.string.theme_mullvad)
        AppTheme.WINDSCRIBE -> stringResource(R.string.theme_windscribe)
        AppTheme.NOTHING -> stringResource(R.string.theme_nothing)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.7f)
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(16.dp),
                    alpha = 0.95f,
                    shadowElevation = if (isSelected) 8.dp else 0.dp
                )
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) colors.brandNorm else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiniDashboardPreview(theme = theme)

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .background(colors.brandNorm, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = ProtonIcons.CheckmarkCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = themeName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) colors.brandNorm else colors.textNorm
        )
    }
}

@Composable
fun MiniDashboardPreview(
    theme: AppTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val themeColors = when (theme) {
        AppTheme.SYSTEM -> {
            if (dynamicColorSupported) {
                val scheme = if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                ProtonColors.fromMaterial3(scheme, isSystemDark)
            } else {
                if (isSystemDark) ProtonColors.Dark else ProtonColors.Light
            }
        }
        AppTheme.LIGHT -> ProtonColors.Light
        AppTheme.DARK -> ProtonColors.Dark
        AppTheme.AMOLED -> ProtonColors.Amoled
        AppTheme.GOLD_LIGHT -> ProtonColors.GoldLight
        AppTheme.GOLD_DARK -> ProtonColors.GoldDark
        AppTheme.GOLD_AMOLED -> ProtonColors.GoldAmoled
        AppTheme.SURFSHARK -> ProtonColors.Surfshark
        AppTheme.NORD -> ProtonColors.Nord
        AppTheme.IPVANISH -> ProtonColors.IPVanish
        AppTheme.PUREVPN -> ProtonColors.PureVPN
        AppTheme.MULLVAD -> ProtonColors.Mullvad
        AppTheme.WINDSCRIBE -> ProtonColors.Windscribe
        AppTheme.NOTHING -> ProtonColors.Nothing
    }

    CompositionLocalProvider(LocalColors provides themeColors) {
        val colors = ProtonNextTheme.colors
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundNorm)
        ) {
            // Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.brandNorm.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
            )

            // Mini Map Placeholder (simplified dots)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        repeat(4) { colIndex ->
                            val alpha = (rowIndex + colIndex) % 2 * 0.1f + 0.05f
                            Box(
                                modifier = Modifier
                                    .size(if (colIndex == 2 && rowIndex == 1) 6.dp else 4.dp)
                                    .background(
                                        if (colIndex == 2 && rowIndex == 1) colors.brandNorm
                                        else colors.textNorm.copy(alpha = alpha),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mini Top Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_proton_lock_open_filled_2),
                        contentDescription = null,
                        tint = colors.notificationError,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(8.dp)
                            .background(colors.textNorm.copy(alpha = 0.4f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Mini Connection Card
                Surface(
                    color = colors.backgroundSecondary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.shade100.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Label
                        Box(modifier = Modifier.width(36.dp).height(6.dp).background(colors.textNorm.copy(alpha = 0.6f), CircleShape))
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Location Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp, 16.dp)
                                    .background(colors.shade20, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Box(modifier = Modifier.width(60.dp).height(8.dp).background(colors.textNorm, CircleShape))
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.width(40.dp).height(6.dp).background(colors.textWeak, CircleShape))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Mini Connect Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(colors.brandNorm, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                             Box(
                                 modifier = Modifier
                                     .width(50.dp)
                                     .height(8.dp)
                                     .background(Color.White.copy(alpha = 0.9f), CircleShape)
                             )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
