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

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingAppsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SplitTunnelingAppsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    val isTablet = isTablet()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient matching settings (immersive)
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
                    .statusBarsPadding(),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
            ) {
                NavigationHeader(
                    title = stringResource(
                        if (uiState.splitTunnelingMode == "exclude") R.string.settings_excluded_apps
                        else R.string.settings_included_apps
                    ),
                    onBack = onBack,
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    ProtonIcons.ThreeDotsVertical,
                                    contentDescription = stringResource(R.string.desc_more_options),
                                    tint = colors.textNorm
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(colors.backgroundSecondary)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = uiState.showSystemApps,
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = colors.brandNorm,
                                                    uncheckedColor = colors.textWeak,
                                                    checkmarkColor = colors.textInverted
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.st_show_system_apps),
                                                color = colors.textNorm
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleShowSystemApps(!uiState.showSystemApps)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                )

                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                LazyColumn(
                    modifier = contentModifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(contentType = "Header") {
                        // Header Icon
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
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
                                    imageVector = ProtonIcons.Grid3,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = colors.brandNorm
                                )
                            }
                        }

                        // Title
                        Text(
                            text = stringResource(
                                if (uiState.splitTunnelingMode == "exclude") R.string.settings_excluded_apps
                                else R.string.settings_included_apps
                            ),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            text = stringResource(R.string.settings_split_tunneling_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    item(contentType = "SearchBar") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Search Bar
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.st_search_apps_hint),
                                        color = colors.textWeak
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        ProtonIcons.Magnifier,
                                        contentDescription = null,
                                        tint = colors.iconWeak
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                    unfocusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                ),
                                singleLine = true
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        item(contentType = "Loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ExpressiveCircularProgressIndicator(color = colors.brandNorm)
                            }
                        }
                    } else {
                        // Selected Apps Section
                        if (uiState.selectedApps.isNotEmpty()) {
                            item(contentType = "SectionHeader") {
                                SectionHeader(
                                    stringResource(
                                        R.string.st_selected_apps_header,
                                        uiState.selectedApps.size
                                    )
                                )
                            }
                            items(uiState.selectedApps, key = { it.packageName }, contentType = { "App" }) { app ->
                                AppListItem(
                                    app = app,
                                    isAdded = true,
                                    onClick = { viewModel.toggleApp(app.packageName, false) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Available Apps Section
                        if (uiState.availableApps.isNotEmpty()) {
                            item(contentType = "SectionHeader") {
                                SectionHeader(
                                    stringResource(
                                        R.string.st_available_apps_header,
                                        uiState.availableApps.size
                                    )
                                )
                            }
                            items(uiState.availableApps, key = { it.packageName }, contentType = { "App" }) { app ->
                                AppListItem(
                                    app = app,
                                    isAdded = false,
                                    onClick = { viewModel.toggleApp(app.packageName, true) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Bottom Spacer
                        item(contentType = "Spacer") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = ProtonNextTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = colors.brandNorm,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun AppListItem(
    app: AppInfo,
    isAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.3f, shadowElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIconWrapper(packageName = app.packageName)

            Spacer(modifier = Modifier.width(16.dp))

            // App Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.textNorm,
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    maxLines = 1
                )
            }

            // Action Icon (Plus / Cross)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAdded) colors.shade20
                        else colors.brandNorm.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdded) ProtonIcons.Cross else ProtonIcons.Plus,
                    contentDescription = null,
                    tint = if (isAdded) colors.iconWeak else colors.brandNorm,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AppIconWrapper(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors

    // Fetch drawable safely
    val drawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            // Render Android Drawable inside Compose natively
            AndroidView(
                modifier = Modifier.size(36.dp),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(drawable)
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(drawable)
                }
            )
        } else {
            // Fallback icon
            Icon(
                imageVector = ProtonIcons.BrandAndroid,
                contentDescription = null,
                tint = colors.iconWeak,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
