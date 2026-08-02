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

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.LoadIndicator
import ru.protonmod.next.ui.components.LoadProgressBar
import ru.protonmod.next.ui.components.MainHeader
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet
import ru.protonmod.next.utils.ProtonLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onNavigateToHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CountriesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedServer by viewModel.connectedServer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTablet = isTablet()

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("CountriesScreen", "VPN permission granted")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
        }
    }

    val errorAppOpsMsg = stringResource(R.string.error_system_appops)

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
            Toast.makeText(context, errorAppOpsMsg, Toast.LENGTH_LONG).show()
            connectAction()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.backgroundNorm,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                AnimatedContent(
                    targetState = uiState,
                    contentKey = { state ->
                        // Use the class as key to avoid transition animations between different Success states
                        // (e.g. when opening/closing the bottom sheet), but still animate between Loading/Success/Error.
                        state::class
                    },
                    label = "countries_state_root",
                    modifier = Modifier.weight(1f)
                ) { state ->
                    when (state) {
                        is CountriesUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ExpressiveCircularProgressIndicator(color = colors.brandNorm)
                            }
                        }
                        is CountriesUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.message, color = colors.notificationError)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.loadServers() },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.interactionNorm)
                                    ) {
                                        Text(stringResource(R.string.btn_retry), color = colors.textInverted)
                                    }
                                }
                            }
                        }
                        is CountriesUiState.Success -> {
                            val countries = remember(state.countries) { state.countries.toImmutableList() }
                            
                            CountriesListContent(
                                countries = countries,
                                connectedServer = connectedServer,
                                onCountryClick = { country ->
                                    checkVpnAndConnect {
                                        viewModel.selectCountry(country.code)
                                        onNavigateToHome()
                                    }
                                },
                                onCountryMore = { country ->
                                    viewModel.expandCitiesForCountry(country.code)
                                },
                                isTablet = isTablet,
                                loadDisplayMode = state.loadDisplayMode,
                                connectionMode = state.connectionMode,
                                onModeSelected = viewModel::setConnectionMode,
                            )
                        }
                    }
                }

                val successState by remember {
                    derivedStateOf { uiState as? CountriesUiState.Success }
                }

                if (successState?.bottomSheetContent != null) {
                    CountriesBottomSheet(
                        onDismiss = { viewModel.backToCountries() },
                        content = successState?.bottomSheetContent!!,
                        connectedServer = connectedServer,
                        onCityClick = { city ->
                            checkVpnAndConnect {
                                viewModel.selectCity(city.name)
                                onNavigateToHome()
                            }
                        },
                        onCityMore = { city ->
                            viewModel.expandServersForCity(city.name)
                        },
                        onServerClick = { server ->
                            checkVpnAndConnect {
                                viewModel.selectServer(server)
                                onNavigateToHome()
                            }
                        },
                        onBack = { viewModel.backToCities() },
                        loadDisplayMode = successState?.loadDisplayMode ?: ServerLoadDisplayMode.ALL
                    )
                }
            }
        }
    }
}

@Composable
fun CountriesListContent(
    countries: ImmutableList<CountryDisplayItem>,
    connectedServer: LogicalServer?,
    onCountryClick: (CountryDisplayItem) -> Unit,
    onCountryMore: (CountryDisplayItem) -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    loadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
    connectionMode: CountryConnectionMode = CountryConnectionMode.STANDARD,
    onModeSelected: (CountryConnectionMode) -> Unit = {},
) {
    val colors = ProtonNextTheme.colors

    Box(modifier = modifier) {
        if (isTablet) {
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }.value
            val columns = (screenWidthDp / 300).toInt().coerceAtLeast(2)
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "Header") {
                    Column {
                        MainHeader(title = stringResource(R.string.countries_title))
                        ConnectionModeSelector(connectionMode, onModeSelected)
                    }
                }

                items(countries, key = { it.code }, contentType = { "Country" }) { country ->
                    CountryCard(
                        country = country,
                        isConnected = connectedServer?.exitCountry == country.code,
                        onClick = { onCountryClick(country) },
                        onMoreClick = { onCountryMore(country) },
                        displayMode = loadDisplayMode,
                        showTorBadge = connectionMode == CountryConnectionMode.TOR,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(contentType = "Header") {
                    Column {
                        MainHeader(title = stringResource(R.string.countries_title))
                        ConnectionModeSelector(connectionMode, onModeSelected)
                    }
                }

                items(countries, key = { it.code }, contentType = { "Country" }) { country ->
                    CountryCard(
                        country = country,
                        isConnected = connectedServer?.exitCountry == country.code,
                        onClick = { onCountryClick(country) },
                        onMoreClick = { onCountryMore(country) },
                        displayMode = loadDisplayMode,
                        showTorBadge = connectionMode == CountryConnectionMode.TOR,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionModeSelector(
    selected: CountryConnectionMode,
    onSelected: (CountryConnectionMode) -> Unit,
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CountryConnectionMode.entries.forEach { mode ->
            val title = when (mode) {
                CountryConnectionMode.STANDARD -> stringResource(R.string.connection_mode_standard)
                CountryConnectionMode.TOR -> stringResource(R.string.connection_mode_tor)
            }
            Button(
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == mode) colors.brandNorm else colors.backgroundSecondary,
                    contentColor = if (selected == mode) colors.textInverted else colors.textNorm,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                if (mode == CountryConnectionMode.TOR) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_tor_project),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(title, maxLines = 1)
            }
        }
    }
}

@Composable
fun CountryCard(
    country: CountryDisplayItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isConnected: Boolean = false,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
    showTorBadge: Boolean = false,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val flagResId = remember(country.code) { CountryUtils.getFlagResource(context, country.code) }
    val localizedName = remember(country.code) { CountryUtils.getCountryName(context, country.code) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (flagResId != 0) {
                        FlagIcon(
                            countryFlag = flagResId,
                            size = DpSize(36.dp, 24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp, 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ProtonIcons.Globe,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (showTorBadge) {
                        Box(
                            modifier = Modifier
                                .offset(x = 5.dp, y = 5.dp)
                                .size(16.dp)
                                .background(colors.backgroundNorm, CircleShape)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_tor_project),
                                contentDescription = stringResource(R.string.connection_mode_tor),
                                tint = colors.brandNorm,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (isConnected) {
                        Box(
                            modifier = Modifier
                                .offset(x = 4.dp, y = 4.dp)
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                                .padding(2.dp)
                                .background(colors.backgroundNorm, CircleShape)
                                .padding(1.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = localizedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = country.averageLoad, displayMode = displayMode)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = ProtonIcons.ThreeDotsVertical,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = country.averageLoad, displayMode = displayMode)
        }
    }
}

@Composable
fun CityCard(
    city: CityDisplayItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isConnected: Boolean = false,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.backgroundNorm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ProtonIcons.Buildings,
                            contentDescription = null,
                            tint = colors.iconNorm,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .offset(x = 4.dp, y = 4.dp)
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                                .padding(2.dp)
                                .background(colors.backgroundNorm, CircleShape)
                                .padding(1.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = city.localizedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = city.averageLoad, displayMode = displayMode)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = ProtonIcons.ThreeDotsVertical,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = city.averageLoad, displayMode = displayMode)
        }
    }
}

@Composable
fun ServerItemCard(
    server: LogicalServer,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadIndicator(load = server.averageLoad, displayMode = displayMode)
                    if (isConnected) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colors.notificationSuccess, CircleShape)
                        )
                    }
                }
            }
            LoadProgressBar(load = server.averageLoad, displayMode = displayMode)
        }
    }
}
