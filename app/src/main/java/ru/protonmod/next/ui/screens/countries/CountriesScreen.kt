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
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onBack: () -> Unit,
    viewModel: CountriesViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val context = LocalContext.current
    val currentTarget = MainTarget.Countries

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("CountriesScreen", "VPN permission granted")
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
            android.widget.Toast.makeText(context, context.getString(R.string.error_system_appops), android.widget.Toast.LENGTH_LONG).show()
            connectAction()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.countries_title), fontWeight = FontWeight.Bold, color = colors.textNorm) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.backgroundNorm
                )
            )
        },
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundNorm)
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState,
                label = "countries_state",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    is CountriesUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandNorm)
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
                    is CountriesUiState.CountriesList -> {
                        CountriesListContent(
                            countries = state.countries,
                            connectedServer = connectedServer,
                            onCountryClick = { country ->
                                checkVpnAndConnect {
                                    viewModel.selectCountry(country.code)
                                    onNavigateToHome()
                                }
                            },
                            onCountryMore = { country ->
                                viewModel.expandCitiesForCountry(country.code)
                            }
                        )
                    }
                    is CountriesUiState.CitiesList -> {
                        CitiesListContent(
                            countryName = state.country,
                            cities = state.cities,
                            connectedServer = connectedServer,
                            onBack = { viewModel.backToCountries() },
                            onCityClick = { city ->
                                checkVpnAndConnect {
                                    viewModel.selectCity(city.name)
                                    onNavigateToHome()
                                }
                            },
                            onCityMore = { city ->
                                viewModel.expandServersForCity(city.name)
                            }
                        )
                    }
                    is CountriesUiState.ServersList -> {
                        ServersListContent(
                            countryName = state.country,
                            cityName = state.city,
                            servers = state.servers,
                            connectedServer = connectedServer,
                            onBack = { viewModel.backToCities() },
                            onServerClick = { server ->
                                checkVpnAndConnect {
                                    viewModel.selectServer(server)
                                    onNavigateToHome()
                                }
                            }
                        )
                    }
                }
            }

            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                navigateTo = { target ->
                    when (target) {
                        MainTarget.Home -> onNavigateToHome()
                        MainTarget.Settings -> onNavigateToSettings()
                        MainTarget.Profiles -> onNavigateToProfiles()
                        MainTarget.Countries -> { }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
fun CountriesListContent(
    countries: List<CountryDisplayItem>,
    connectedServer: LogicalServer?,
    onCountryClick: (CountryDisplayItem) -> Unit,
    onCountryMore: (CountryDisplayItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(countries) { country ->
            CountryCard(
                country = country,
                isConnected = connectedServer?.exitCountry == country.code,
                onClick = { onCountryClick(country) },
                onMoreClick = { onCountryMore(country) }
            )
        }
    }
}

@Composable
fun CountryCard(
    country: CountryDisplayItem,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val flagResId = CountryUtils.getFlagResource(context, country.code)
    val localizedName = CountryUtils.getCountryName(context, country.code)
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundSecondary.copy(alpha = 0.5f)
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
                                imageVector = Icons.Rounded.Public,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                    text = localizedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = country.averageLoad)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = country.averageLoad)
        }
    }
}

@Composable
fun CitiesListContent(
    countryName: String,
    cities: List<CityDisplayItem>,
    connectedServer: LogicalServer?,
    onBack: () -> Unit,
    onCityClick: (CityDisplayItem) -> Unit,
    onCityMore: (CityDisplayItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val backInteractionSource = remember { MutableInteractionSource() }
            val colors = ProtonNextTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBack
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.desc_back_button),
                        tint = colors.textNorm
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = countryName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                }
            }
        }

        items(cities) { city ->
            CityCard(
                city = city,
                isConnected = (connectedServer?.city == city.name && connectedServer.exitCountry == countryName),
                onClick = { onCityClick(city) },
                onMoreClick = { onCityMore(city) }
            )
        }
    }
}

@Composable
fun CityCard(
    city: CityDisplayItem,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundSecondary.copy(alpha = 0.5f)
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
                            imageVector = Icons.Default.LocationCity,
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
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                LoadIndicator(load = city.averageLoad)

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.desc_more_options),
                        tint = colors.iconWeak
                    )
                }
            }

            LoadProgressBar(load = city.averageLoad)
        }
    }
}

@Composable
fun ServersListContent(
    countryName: String,
    cityName: String,
    servers: List<LogicalServer>,
    connectedServer: LogicalServer?,
    onBack: () -> Unit,
    onServerClick: (LogicalServer) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val backInteractionSource = remember { MutableInteractionSource() }
            val colors = ProtonNextTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBack
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.desc_back_button),
                        tint = colors.textNorm
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$countryName, $cityName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                }
            }
        }

        items(servers) { server ->
            ServerItemCard(
                server = server,
                isConnected = connectedServer?.id == server.id,
                onClick = { onServerClick(server) }
            )
        }
    }
}

@Composable
fun ServerItemCard(
    server: LogicalServer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) colors.brandNorm.copy(alpha = 0.1f) else colors.backgroundSecondary.copy(alpha = 0.5f)
        )
    ) {
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
                Text(
                    text = server.servers.firstOrNull()?.exitIp ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadIndicator(load = server.averageLoad)
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
    }
}

@Composable
fun LoadIndicator(load: Int) {
    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$load%",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LoadProgressBar(load: Int) {
    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    LinearProgressIndicator(
        progress = { load / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = color,
        trackColor = color.copy(alpha = 0.1f)
    )
}
