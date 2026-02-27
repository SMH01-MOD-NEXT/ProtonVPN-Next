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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Android
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingAppsScreen(
    onBack: () -> Unit = {},
    viewModel: SplitTunnelingAppsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_excluded_apps),
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient matching settings
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Search Bar
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    placeholder = {
                        Text(
                            "Search apps",
                            color = colors.textWeak
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.iconWeak
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.7f),
                        unfocusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.7f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.textNorm,
                        unfocusedTextColor = colors.textNorm
                    ),
                    singleLine = true
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brandNorm)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {

                            // Selected Apps Section
                            if (uiState.selectedApps.isNotEmpty()) {
                                item {
                                    SectionHeader("Selected Apps (${uiState.selectedApps.size})")
                                }
                                items(uiState.selectedApps, key = { it.packageName }) { app ->
                                    AppListItem(
                                        app = app,
                                        isAdded = true,
                                        onClick = { viewModel.toggleApp(app.packageName, false) }
                                    )
                                }
                            }

                            // Available Apps Section
                            if (uiState.availableApps.isNotEmpty()) {
                                item {
                                    SectionHeader("Available Apps (${uiState.availableApps.size})")
                                }
                                items(uiState.availableApps, key = { it.packageName }) { app ->
                                    AppListItem(
                                        app = app,
                                        isAdded = false,
                                        onClick = { viewModel.toggleApp(app.packageName, true) }
                                    )
                                }
                            }

                            // Bottom Spacer
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
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
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
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
                imageVector = if (isAdded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                tint = if (isAdded) colors.iconWeak else colors.brandNorm,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AppIconWrapper(packageName: String) {
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
        modifier = Modifier.size(40.dp),
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
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = colors.iconWeak,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
