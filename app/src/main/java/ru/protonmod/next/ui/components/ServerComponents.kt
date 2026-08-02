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

package ru.protonmod.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils

@Composable
fun ServerCard(
    server: LogicalServer,
    isConnected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
    onClick: (() -> Unit)? = null,
    alpha: Float? = null,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = alpha ?: if (isConnected) 0.3f else 0.4f,
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
                                    imageVector = ProtonIcons.Earth,
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
                    LoadIndicator(
                        load = server.averageLoad,
                        displayMode = displayMode
                    )
                }
            }

            LoadProgressBar(
                load = server.averageLoad,
                displayMode = displayMode
            )
        }
    }
}

@Composable
fun LoadIndicator(
    load: Int,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    if (displayMode == ServerLoadDisplayMode.HIDDEN || displayMode == ServerLoadDisplayMode.LINE) return

    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ProtonIcons.Earth,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.location_load, load),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LoadProgressBar(
    load: Int,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    if (displayMode == ServerLoadDisplayMode.HIDDEN || displayMode == ServerLoadDisplayMode.PERCENT) return

    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    ExpressiveLinearProgressIndicator(
        progress = { load / 100f },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = color,
        trackColor = color.copy(alpha = 0.1f)
    )
}
