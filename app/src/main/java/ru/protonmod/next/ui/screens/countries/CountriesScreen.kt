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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
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
    val uiState by viewModel.uiState.collectAsState()
    val connectedServer by viewModel.connectedServer.collectAsState()
    val currentTarget = MainTarget.Countries

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.countries_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back_button))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {} // We use Box alignment for LiquidGlassBottomBar
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                            CircularProgressIndicator()
                        }
                    }
                    is CountriesUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadServers() }) {
                                    Text(stringResource(R.string.btn_retry))
                                }
                            }
                        }
                    }
                    is CountriesUiState.CountriesList -> {
                        CountriesListContent(
                            countries = state.countries,
                            connectedServer = connectedServer,
                            onCountryClick = { country ->
                                viewModel.selectCountry(country)
                                onNavigateToHome()
                            },
                            onCountryMore = { country ->
                                viewModel.expandCitiesForCountry(country)
                            }
                        )
                    }
                    is CountriesUiState.CitiesList -> {
                        CitiesListContent(
                            country = state.country,
                            cities = state.cities,
                            connectedServer = connectedServer,
                            onBack = { viewModel.backToCountries() },
                            onCityClick = { city ->
                                viewModel.selectCity(city)
                                onNavigateToHome()
                            },
                            onCityMore = { city ->
                                viewModel.expandServersForCity(city)
                            }
                        )
                    }
                    is CountriesUiState.ServersList -> {
                        ServersListContent(
                            country = state.country,
                            city = state.city,
                            servers = state.servers,
                            connectedServer = connectedServer,
                            onBack = { viewModel.backToCities() },
                            onServerClick = { server ->
                                viewModel.selectServer(server)
                                onNavigateToHome()
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
                        MainTarget.Countries -> { /* Already here */ }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun CountriesListContent(
    countries: List<String>,
    connectedServer: LogicalServer?,
    onCountryClick: (String) -> Unit,
    onCountryMore: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(countries) { country ->
            CountryCard(
                country = country,
                isConnected = connectedServer?.exitCountry == country,
                onClick = { onCountryClick(country) },
                onMoreClick = { onCountryMore(country) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun CountryCard(
    country: String,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val flag = CountryUtils.getFlagForCountry(country)
    val localizedName = CountryUtils.getCountryName(context, country)
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
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Text(
                    text = flag,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(40.dp)
                )
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(1.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = localizedName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.desc_more_options)
                )
            }
        }
    }
}

@Composable
fun CitiesListContent(
    country: String,
    cities: List<String>,
    connectedServer: LogicalServer?,
    onBack: () -> Unit,
    onCityClick: (String) -> Unit,
    onCityMore: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val backInteractionSource = remember { MutableInteractionSource() }
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
                        contentDescription = stringResource(R.string.desc_back_button)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = country,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(cities) { city ->
            CityCard(
                city = city,
                isConnected = (connectedServer?.city == city && connectedServer.exitCountry == country),
                onClick = { onCityClick(city) },
                onMoreClick = { onCityMore(city) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun CityCard(
    city: String,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
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
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Text(
                    text = "🏙️",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(40.dp)
                )
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(1.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = city,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.desc_more_options)
                )
            }
        }
    }
}

@Composable
fun ServersListContent(
    country: String,
    city: String,
    servers: List<LogicalServer>,
    connectedServer: LogicalServer?,
    onBack: () -> Unit,
    onServerClick: (LogicalServer) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val backInteractionSource = remember { MutableInteractionSource() }
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
                        contentDescription = stringResource(R.string.desc_back_button)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = country,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = city,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        items(servers) { server ->
            ServerSelectionCard(
                server = server,
                isConnected = connectedServer?.id == server.id,
                onClick = { onServerClick(server) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ServerSelectionCard(
    server: LogicalServer,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
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
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = server.name,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(1.dp)
                            .background(Color(0xFF3DDC84), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = server.city,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
