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

package ru.protonmod.next.ota

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.ui.components.ExpressiveLinearProgressIndicator
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun OTAUpdateScreen(
    uiState: UpdateUiState,
    onInstall: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    val updateInfo = uiState.updateInfo ?: return

    Box(modifier = modifier) {
        if (updateInfo.force) {
            // Fullscreen forced update
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
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .statusBarsPadding()
                ) {
                    // Header Icon
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(colors.brandNorm.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ProtonIcons.ArrowDownCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = stringResource(R.string.ota_force_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textNorm,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ota_force_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    if (uiState.isDownloading) {
                        Spacer(Modifier.height(48.dp))
                        ExpressiveLinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = colors.brandNorm,
                            trackColor = colors.backgroundSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.ota_downloading, (uiState.downloadProgress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textWeak
                        )
                    } else if (uiState.downloadedFile != null) {
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.ota_btn_install), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = { onDownload(updateInfo) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.ota_btn_update), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Optional update dialog
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ProtonIcons.ArrowDownCircle, null, tint = colors.brandNorm)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ota_title))
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.ota_new_version, updateInfo.versionName),
                            fontWeight = FontWeight.Bold,
                            color = colors.textNorm
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ota_changelog), style = MaterialTheme.typography.labelMedium, color = colors.textWeak)
                        Text(updateInfo.changelog, color = colors.textWeak)
                        
                        if (uiState.isDownloading) {
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { uiState.downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!uiState.isDownloading) {
                        if (uiState.downloadedFile != null) {
                            Button(onClick = onInstall) {
                                Text(stringResource(R.string.ota_btn_install))
                            }
                        } else {
                            Button(onClick = { onDownload(updateInfo) }) {
                                Text(stringResource(R.string.ota_btn_update))
                            }
                        }
                    }
                },
                dismissButton = {
                    if (!uiState.isDownloading) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.ota_btn_later))
                        }
                    }
                },
                containerColor = colors.backgroundSecondary,
                shape = RoundedCornerShape(24.dp),
                titleContentColor = colors.textNorm,
                textContentColor = colors.textWeak
            )
        }
    }
}
