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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(
    appVersion: String,
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val githubUrl = stringResource(R.string.url_github)
    val gitlabUrl = stringResource(R.string.url_gitlab)
    val telegramUrl = stringResource(R.string.url_telegram)
    val crowdinUrl = stringResource(R.string.url_crowdin)

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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.settings_about),
                        onBack = onBack
                    )

                    // Header Image (App Logo)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(colors.brandNorm.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_background),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // App name
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Version
                    Text(
                        text = stringResource(R.string.settings_version_short, appVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }

                item(contentType = "Community") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.about_community),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp, start = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AboutLinkCard(
                                title = stringResource(id = R.string.about_github),
                                iconResId = R.drawable.ic_github,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            AboutLinkCard(
                                title = stringResource(id = R.string.about_gitlab),
                                iconResId = R.drawable.ic_gitlab,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, gitlabUrl.toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AboutLinkCard(
                                title = stringResource(id = R.string.about_telegram),
                                iconResId = R.drawable.ic_telegram,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, telegramUrl.toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            AboutLinkCard(
                                title = stringResource(id = R.string.about_crowdin),
                                iconResId = R.drawable.ic_crowdin,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, crowdinUrl.toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                item(contentType = "Licenses") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.settings_legal),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp, start = 12.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    shape = RoundedCornerShape(20.dp),
                                    alpha = 0.4f,
                                    shadowElevation = 0.dp
                                )
                        ) {
                            Column {
                                SettingRowWithIcon(
                                    icon = ProtonIcons.FileLines,
                                    title = stringResource(R.string.settings_licenses),
                                    subtitle = stringResource(R.string.settings_licenses_desc),
                                    onClick = onNavigateToLicenses
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = colors.separatorNorm
                                )
                                SettingRowWithIcon(
                                    icon = ProtonIcons.ShieldHalfFilled,
                                    title = stringResource(R.string.settings_privacy_policy),
                                    subtitle = stringResource(R.string.settings_disclaimer),
                                    onClick = onNavigateToPrivacyPolicy
                                )
                            }
                        }
                    }
                }

                item(contentType = "Sentry") {
                    SentryPoweredBy()
                }
            }
        }
    }
}

@Composable
fun AboutLinkCard(
    title: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 8.dp),
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
                    painter = painterResource(id = iconResId),
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
