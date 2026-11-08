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

package ru.protonmod.next.ui.screens.countries

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesBottomSheet(
    onDismiss: () -> Unit,
    content: BottomSheetContent,
    connectedServer: LogicalServer?,
    onCityClick: (CityDisplayItem) -> Unit,
    onCityMore: (CityDisplayItem) -> Unit,
    onServerClick: (LogicalServer) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
    availability: AvailabilityBadges = AvailabilityBadges.EMPTY
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.backgroundNorm,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.iconWeak) }
    ) {
        AnimatedContent(
            targetState = content,
            transitionSpec = {
                // Determine direction: if coming from Cities to Servers, slide in from right.
                // If going back from Servers to Cities, slide in from left.
                val isGoingForward = targetState is BottomSheetContent.Servers
                
                if (isGoingForward) {
                    (slideInHorizontally { it } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(300)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { it } + fadeOut(tween(300)))
                }.using(SizeTransform(clip = false))
            },
            label = "bottom_sheet_transition",
            modifier = Modifier.fillMaxWidth()
        ) { targetContent ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                when (targetContent) {
                    is BottomSheetContent.Cities -> {
                        val localizedCountry = remember(targetContent.countryCode) {
                            CountryUtils.getCountryName(context, targetContent.countryCode)
                        }
                        NavigationHeader(
                            title = localizedCountry,
                            onBack = onDismiss,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(targetContent.cities, key = { it.name }) { city ->
                                CityCard(
                                    city = city,
                                    isConnected = (connectedServer?.city == city.name && connectedServer.exitCountry == targetContent.countryCode),
                                    onClick = { onCityClick(city) },
                                    onMoreClick = { onCityMore(city) },
                                    displayMode = loadDisplayMode,
                                    availability = availability.city(targetContent.countryCode, city.name)
                                )
                            }
                        }
                    }
                    is BottomSheetContent.Servers -> {
                        val localizedCountry = remember(targetContent.countryCode) {
                            CountryUtils.getCountryName(context, targetContent.countryCode)
                        }
                        NavigationHeader(
                            title = "$localizedCountry, ${targetContent.localizedCityName}",
                            onBack = onBack,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(targetContent.servers, key = { it.id }) { server ->
                                ServerItemCard(
                                    server = server,
                                    isConnected = connectedServer?.id == server.id,
                                    onClick = { onServerClick(server) },
                                    displayMode = loadDisplayMode,
                                    availability = availability.server(server.id)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
