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

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(
    appVersion: String,
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val githubUrl = "https://github.com/SMH01-MOD-NEXT/ProtonMOD-NEXT"
    val telegramUrl = "https://t.me/ProtonVPN_MOD"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_about),
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
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    setImageResource(R.mipmap.ic_launcher)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // App name
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.textNorm
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Version
                        Text(
                            text = "v$appVersion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }

                item {
                    Text(
                        text = stringResource(id = R.string.about_community),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AboutLinkCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(id = R.string.about_github),
                            icon = Icons.Rounded.Code,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                                context.startActivity(intent)
                            }
                        )

                        AboutLinkCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(id = R.string.about_telegram),
                            icon = Icons.Rounded.Info,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                                context.startActivity(intent)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text(
                        text = stringResource(id = R.string.settings_about),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 12.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            SettingRowWithIcon(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                title = stringResource(R.string.settings_licenses),
                                subtitle = stringResource(R.string.settings_licenses_desc),
                                onClick = onNavigateToLicenses
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutLinkCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.2f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.brandNorm.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textNorm,
                textAlign = TextAlign.Center
            )
        }
    }
}
