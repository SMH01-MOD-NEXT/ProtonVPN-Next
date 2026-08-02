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

package ru.protonmod.next.ui.screens.profiles

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.util.Locale
import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.components.LoadIndicator
import ru.protonmod.next.ui.components.LoadProgressBar
import ru.protonmod.next.ui.components.MainHeader
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.components.SmoothTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.screens.countries.CityDisplayItem
import ru.protonmod.next.ui.screens.countries.CountryDisplayItem
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet

// Helper function to dynamically localize city names based on string resources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profileId: String?,
    viewModel: ProfilesViewModel,
    onNavigateToPortSelection: (Int) -> Unit,
    onNavigateToProtocolSelection: (String) -> Unit,
    onNavigateToUrlSelection: (String) -> Unit,
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val customObfuscationConfigs by viewModel.customObfuscationConfigs.collectAsStateWithLifecycle()
    val serverLoadDisplayMode by viewModel.serverLoadDisplayMode.collectAsStateWithLifecycle()
    val isTablet = isTablet()

    val editingProfile = remember(profileId, profiles) {
        profiles.find { it.id == profileId }
    }

    // Seed the editor synchronously when the profile is already present in the
    // ViewModel. The profile id is part of the saveable key, so the state is
    // recreated with real values as soon as an asynchronously loaded profile
    // becomes available, without waiting for a post-composition effect.
    val editorStateKey = editingProfile?.id
    var profileName by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.name.orEmpty()) }
    var targetCountry by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.targetCountry) }
    var targetCity by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.targetCity) }
    var targetCityLocalized by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.localizedCity) }
    var targetServerId by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.targetServerId) }
    var targetServerName by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.targetServerName) }
    var selectedProtocol by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.protocol ?: "AmneziaWG") }
    var selectedPort by rememberSaveable(profileId, editorStateKey) { mutableIntStateOf(editingProfile?.port ?: 0) }
    var autoOpenUrl by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.autoOpenUrl.orEmpty()) }
    var obfuscationEnabled by rememberSaveable(profileId, editorStateKey) { mutableStateOf(editingProfile?.isObfuscationEnabled ?: false) }
    var obfuscationProfileId by rememberSaveable(profileId, editorStateKey) {
        mutableStateOf(editingProfile?.obfuscationProfileId ?: "standard_1")
    }

    var showLocationDialog by remember { mutableStateOf(false) }
    var showObfuscationConfigDialog by remember { mutableStateOf(false) }

    val standardProfileName = stringResource(R.string.obfuscation_config_standard)

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundNorm)) {
        // Navigation and results
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        LaunchedEffect(navBackStackEntry) {
            navBackStackEntry?.savedStateHandle?.get<Int>("selectedPort")?.let { port ->
                selectedPort = port
                navBackStackEntry?.savedStateHandle?.remove<Int>("selectedPort")
            }
            navBackStackEntry?.savedStateHandle?.get<String>("selectedProtocol")?.let { protocol ->
                selectedProtocol = protocol
                navBackStackEntry?.savedStateHandle?.remove<String>("selectedProtocol")
            }
            navBackStackEntry?.savedStateHandle?.get<String>("selectedUrl")?.let { url ->
                autoOpenUrl = url
                navBackStackEntry?.savedStateHandle?.remove<String>("selectedUrl")
            }
        }

        // The editor always renders immediately. If a cold database read is
        // needed, the accent transitions smoothly from the neutral background
        // to the resolved target color instead of hiding the whole screen.
        val accent = remember(targetServerId, targetCity, targetCountry, colors.brandNorm) {
            getProfileAccent(targetServerId, targetCity, targetCountry, colors.brandNorm)
        }
        val animatedBackgroundAccent by animateColorAsState(
            targetValue = if (profileId != null && editingProfile == null) {
                colors.backgroundNorm
            } else {
                accent.end.copy(alpha = 0.30f)
            },
            animationSpec = tween(durationMillis = 220),
            label = "profileBackgroundAccent",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedBackgroundAccent,
                            colors.backgroundNorm.copy(alpha = 0.1f),
                            colors.backgroundNorm
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding(),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                item(contentType = "Header") {
                    NavigationHeader(
                        title = if (profileId == null) stringResource(R.string.title_create_profile) else stringResource(R.string.title_edit_profile),
                        onBack = onNavigateBack,
                        actions = {
                            Button(
                                onClick = {
                                    if (profileName.isBlank()) {
                                        Toast.makeText(context, R.string.error_empty_profile_name, Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    var validatedUrl = autoOpenUrl.trim()
                                    if (validatedUrl.isNotBlank() && !validatedUrl.contains("://")) {
                                        validatedUrl = "https://$validatedUrl"
                                    }

                                    val newProfile = VpnProfileUiModel(
                                        id = profileId ?: UUID.randomUUID().toString(),
                                        name = profileName,
                                        targetCountry = targetCountry,
                                        targetCity = targetCity,
                                        targetServerId = targetServerId,
                                        targetServerName = targetServerName,
                                        protocol = selectedProtocol,
                                        port = selectedPort,
                                        autoOpenUrl = validatedUrl,
                                        isObfuscationEnabled = obfuscationEnabled,
                                        obfuscationProfileId = if (obfuscationEnabled) obfuscationProfileId else null
                                    )
                                    viewModel.saveProfile(newProfile)
                                    onNavigateBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(stringResource(R.string.btn_save), color = colors.onInteraction, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                item(contentType = "Preview") {
                    val previewSubtitle = when {
                        targetServerId != null -> stringResource(R.string.location_server, (targetServerName ?: targetServerId) as Any)
                        targetCity != null -> {
                            val city = targetCityLocalized ?: targetCity!!
                            val country = CountryUtils.getCountryName(context, targetCountry)
                            stringResource(R.string.location_city_format, country, city)
                        }
                        targetCountry != null -> CountryUtils.getCountryName(context, targetCountry)
                        else -> stringResource(R.string.location_fastest)
                    }
                    ProfileHeroPreview(
                        name = profileName.ifBlank { stringResource(R.string.label_profile_name) },
                        subtitle = previewSubtitle,
                        protocolLabel = selectedProtocol,
                        portLabel = if (selectedPort == 0) stringResource(R.string.settings_port_auto) else selectedPort.toString(),
                        targetServerId = targetServerId,
                        targetCity = targetCity,
                        targetCountry = targetCountry,
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    )
                }

                item(contentType = "Category") {
                    SettingsCategory(title = stringResource(R.string.category_general), modifier = contentModifier.padding(horizontal = 16.dp)) {
                        SettingTextFieldRow(
                            label = stringResource(R.string.label_profile_name),
                            value = profileName,
                            onValueChange = { profileName = it }
                        )
                    }
                }

                item(contentType = "Category") {
                    SettingsCategory(title = stringResource(R.string.category_connection), modifier = contentModifier.padding(horizontal = 16.dp)) {
                        val locationSubtitle = when {
                            targetServerId != null -> stringResource(R.string.location_server, (targetServerName ?: targetServerId) as Any)
                            targetCity != null -> {
                                val city = targetCityLocalized ?: targetCity!!
                                val country = CountryUtils.getCountryName(context, targetCountry)
                                stringResource(R.string.location_city_format, country, city)
                            }
                            targetCountry != null -> CountryUtils.getCountryName(context, targetCountry)
                            else -> stringResource(R.string.location_fastest)
                        }

                        SettingRowWithIcon(
                            title = stringResource(R.string.label_location),
                            subtitle = locationSubtitle,
                            countryCode = targetCountry,
                            onClick = { showLocationDialog = true }
                        )

                        SettingRowWithIcon(
                            title = stringResource(R.string.label_protocol),
                            subtitle = selectedProtocol,
                            icon = ProtonIcons.Shield,
                            onClick = { onNavigateToProtocolSelection(selectedProtocol) }
                        )

                        SettingRowWithIcon(
                            title = stringResource(R.string.label_port),
                            subtitle = (if (selectedPort == 0) stringResource(R.string.settings_port_auto) else selectedPort.toString()),
                            icon = ProtonIcons.PowerOff,
                            onClick = { onNavigateToPortSelection(selectedPort) }
                        )
                    }
                }

                item(contentType = "Category") {
                    SettingsCategory(title = stringResource(R.string.category_advanced), modifier = contentModifier.padding(horizontal = 16.dp)) {
                        SettingToggleRow(
                            title = stringResource(R.string.label_obfuscation),
                            subtitle = stringResource(R.string.obfuscation_desc),
                            icon = ProtonIcons.EyeSlash,
                            checked = obfuscationEnabled,
                            onCheckedChange = { obfuscationEnabled = it }
                        )

                        if (obfuscationEnabled) {
                            val allConfigs = listOf(ObfuscationProfile.getStandardProfile(standardProfileName)) + customObfuscationConfigs
                            val selectedConfig = allConfigs.find { it.id == obfuscationProfileId } ?: allConfigs.first()

                            SettingRowWithIcon(
                                title = stringResource(R.string.label_obfuscation_config),
                                subtitle = selectedConfig.name,
                                icon = ProtonIcons.CogWheel,
                                onClick = { showObfuscationConfigDialog = true },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                item(contentType = "Category") {
                    SettingsCategory(title = stringResource(R.string.category_automation), modifier = contentModifier.padding(horizontal = 16.dp)) {
                        SettingRowWithIcon(
                            title = stringResource(R.string.label_connect_go_website),
                            subtitle = autoOpenUrl.ifEmpty { stringResource(R.string.label_not_configured) },
                            icon = ProtonIcons.ArrowOutSquare,
                            onClick = { onNavigateToUrlSelection(autoOpenUrl) }
                        )
                    }

                    Text(
                        text = stringResource(R.string.automation_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        modifier = contentModifier.padding(start = 28.dp, top = 8.dp, end = 28.dp)
                    )
                }

                if (profileId != null) {
                    item(contentType = "DeleteButton") {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                viewModel.deleteProfile(profileId)
                                onNavigateBack()
                            },
                            modifier = contentModifier.padding(horizontal = 16.dp).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(ProtonIcons.Trash, contentDescription = null, tint = Color.Red)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_delete), color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showLocationDialog) {
            LocationSelectionDialog(
                countries = countries.toImmutableList(),
                onGetCities = viewModel::getCitiesForCountry,
                onGetServers = viewModel::getServersForCity,
                onLocationSelect = { country, city, cityLocalized, serverId, serverName ->
                    targetCountry = country
                    targetCity = city
                    targetCityLocalized = cityLocalized
                    targetServerId = serverId
                    targetServerName = serverName
                    showLocationDialog = false
                },
                onDismiss = { showLocationDialog = false },
                selectedCountry = targetCountry,
                selectedCity = targetCity,
                loadDisplayMode = serverLoadDisplayMode
            )
        }

        if (showObfuscationConfigDialog) {
            val newConfigName = stringResource(R.string.custom_config_name, customObfuscationConfigs.size + 1)

            ObfuscationConfigSelectionDialog(
                configs = (listOf(ObfuscationProfile.getStandardProfile(standardProfileName)) + customObfuscationConfigs).toImmutableList(),
                selectedId = obfuscationProfileId,
                onDismiss = { showObfuscationConfigDialog = false },
                onConfigSelect = {
                    obfuscationProfileId = it
                    showObfuscationConfigDialog = false
                },
                onCreateNew = {
                    val newConfig = ObfuscationProfile.createDefaultCustomProfile(
                        id = UUID.randomUUID().toString(),
                        name = newConfigName
                    )
                    viewModel.saveObfuscationProfile(newConfig)
                    obfuscationProfileId = newConfig.id
                    showObfuscationConfigDialog = false
                },
                onEdit = { profile ->
                    viewModel.saveObfuscationProfile(profile)
                },
                onDelete = { profileIdToDelete ->
                    viewModel.deleteObfuscationProfile(profileIdToDelete)
                    if (obfuscationProfileId == profileIdToDelete) {
                        obfuscationProfileId = "standard_1"
                    }
                }
            )
        }
    }
}

@Composable
fun SelectionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    load: Int? = null,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
    icon: @Composable () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.5f)
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp, 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (load != null) {
                    LoadIndicator(load = load, displayMode = displayMode)
                }
            }

            if (load != null) {
                LoadProgressBar(load = load, displayMode = displayMode)
            }
        }
    }
}

@Composable
fun LocationSelectionDialog(
    countries: ImmutableList<CountryDisplayItem>,
    onGetCities: suspend (String) -> List<CityDisplayItem>,
    onGetServers: suspend (String, String) -> List<LogicalServer>,
    onLocationSelect: (country: String?, city: String?, cityLocalized: String?, serverId: String?, serverName: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedCountry: String? = null,
    selectedCity: String? = null,
    loadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) } // 0: Country, 1: City, 2: Server
    var currentCountry by remember { mutableStateOf(selectedCountry) }
    var currentCity by remember { mutableStateOf(selectedCity) }
    var currentCityLocalized by remember { mutableStateOf<String?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }

    var cities by remember { mutableStateOf<List<CityDisplayItem>>(emptyList()) }
    var servers by remember { mutableStateOf<List<LogicalServer>>(emptyList()) }

    Box(modifier = modifier) {
        Dialog(onDismissRequest = onDismiss) {
            Box(modifier = Modifier) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.backgroundNorm),
                    border = BorderStroke(1.dp, colors.shade100.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (step > 0) {
                                IconButton(onClick = { step-- }, enabled = !isTransitioning) {
                                    Icon(
                                        imageVector = ProtonIcons.ArrowLeft,
                                        contentDescription = stringResource(R.string.desc_back),
                                        tint = colors.textNorm
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = when (step) {
                                    0 -> stringResource(R.string.title_select_country)
                                    1 -> stringResource(R.string.title_select_city)
                                    else -> {
                                        val countryName = CountryUtils.getCountryName(context, currentCountry)
                                        val cityName = currentCityLocalized ?: currentCity ?: ""
                                        if (cityName.isNotEmpty()) "$countryName, $cityName" else stringResource(R.string.title_select_server)
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textNorm,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = onDismiss) {
                                Icon(ProtonIcons.Cross, contentDescription = stringResource(R.string.desc_close), tint = colors.iconWeak)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.shade20.copy(alpha = 0.5f))

                        AnimatedContent(
                            targetState = step,
                            label = "location_step",
                            modifier = Modifier.weight(1f)
                        ) { currentStep ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(contentType = "QuickSelect") {
                                    SelectionCard(
                                        title = when (currentStep) {
                                            0 -> stringResource(R.string.location_fastest)
                                            1 -> stringResource(R.string.location_fastest_in_country, CountryUtils.getCountryName(context, currentCountry))
                                            else -> stringResource(R.string.location_fastest_in_city, currentCityLocalized ?: currentCity ?: "")
                                        },
                                        displayMode = loadDisplayMode,
                                        icon = {
                                            FlagIcon(
                                                countryFlag = R.drawable.flag_fastest,
                                                size = DpSize(36.dp, 24.dp)
                                            )
                                        },
                                        onClick = {
                                            if (!isTransitioning) {
                                                when (currentStep) {
                                                    0 -> onLocationSelect(null, null, null, null, null)
                                                    1 -> onLocationSelect(currentCountry, null, null, null, null)
                                                    2 -> onLocationSelect(currentCountry, currentCity, currentCityLocalized, null, null)
                                                }
                                            }
                                        }
                                    )
                                }

                                when (currentStep) {
                                    0 -> {
                                        items(countries, key = { it.code }, contentType = { "Country" }) { countryItem ->
                                            val localizedName = CountryUtils.getCountryName(context, countryItem.code)
                                            val flagResId = CountryUtils.getFlagResource(context, countryItem.code)
                                            SelectionCard(
                                                title = localizedName,
                                                icon = {
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
                                                            Icon(imageVector = ProtonIcons.Globe, contentDescription = null, tint = colors.iconNorm, modifier = Modifier.size(20.dp))
                                                        }
                                                    }
                                                },
                                                load = countryItem.averageLoad,
                                                displayMode = loadDisplayMode,
                                                onClick = {
                                                    if (!isTransitioning) {
                                                        scope.launch {
                                                            isTransitioning = true
                                                            cities = onGetCities(countryItem.code)
                                                            currentCountry = countryItem.code
                                                            step = 1
                                                            isTransitioning = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    1 -> {
                                        items(cities, key = { it.name }, contentType = { "City" }) { cityItem ->
                                            SelectionCard(
                                                title = cityItem.localizedName,
                                                icon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp, 24.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(colors.backgroundNorm),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(imageVector = ProtonIcons.Buildings, contentDescription = null, tint = colors.iconNorm, modifier = Modifier.size(20.dp))
                                                    }
                                                },
                                                load = cityItem.averageLoad,
                                                displayMode = loadDisplayMode,
                                                onClick = {
                                                    if (!isTransitioning) {
                                                        scope.launch {
                                                            isTransitioning = true
                                                            servers = onGetServers(currentCountry!!, cityItem.name)
                                                            currentCity = cityItem.name
                                                            currentCityLocalized = cityItem.localizedName
                                                            step = 2
                                                            isTransitioning = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    else -> {
                                        items(servers, key = { it.id }, contentType = { "Server" }) { server ->
                                            SelectionCard(
                                                title = server.name,
                                                icon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp, 24.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(colors.backgroundNorm),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = ProtonIcons.Globe,
                                                            contentDescription = null,
                                                            tint = colors.iconNorm,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                load = server.averageLoad,
                                                displayMode = loadDisplayMode,
                                                onClick = {
                                                    if (!isTransitioning) {
                                                        onLocationSelect(currentCountry, currentCity, currentCityLocalized, server.id, server.name)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit),
) {
    val colors = ProtonNextTheme.colors
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm,
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingRowWithIcon(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    countryCode: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val isLocation = title == stringResource(R.string.label_location)

    var baseModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        baseModifier = baseModifier.clickable(onClick = onClick)
    }
    baseModifier = baseModifier.padding(vertical = 12.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || countryCode != null || isLocation) {
            val flagResId = CountryUtils.getFlagResource(context, countryCode)

            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(48.dp, 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.brandNorm.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (flagResId != 0) {
                    FlagIcon(
                        countryFlag = flagResId,
                        size = DpSize(36.dp, 24.dp)
                    )
                } else if (countryCode != null) {
                    Text(text = CountryUtils.getFlagForCountry(countryCode), style = MaterialTheme.typography.titleMedium)
                } else if (isLocation) {
                    FlagIcon(
                        countryFlag = R.drawable.flag_fastest,
                        size = DpSize(36.dp, 24.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.brandNorm,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textNorm
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = ProtonIcons.ChevronRight,
                contentDescription = null,
                tint = colors.iconWeak,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colors = ProtonNextTheme.colors
    SettingRowWithIcon(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.brandNorm,
                    uncheckedThumbColor = colors.shade60,
                    uncheckedTrackColor = colors.shade20
                )
            )
        }
    )
}

@Composable
fun SettingTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    SmoothOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textNorm,
            unfocusedTextColor = colors.textNorm,
            focusedBorderColor = colors.brandNorm,
            unfocusedBorderColor = colors.shade20,
            focusedLabelColor = colors.brandNorm,
            unfocusedLabelColor = colors.textWeak
        ),
        singleLine = true
    )
}

@Composable
fun ObfuscationConfigSelectionDialog(
    configs: ImmutableList<ObfuscationProfile>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onConfigSelect: (String) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (ObfuscationProfile) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var editingProfile by remember { mutableStateOf<ObfuscationProfile?>(null) }

    Box(modifier = modifier) {
        Dialog(onDismissRequest = onDismiss) {
            Box(modifier = Modifier) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = stringResource(R.string.title_select_obfuscation_config),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textNorm,
                            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                        )

                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(configs, key = { it.id }, contentType = { "Config" }) { config ->
                                val isSelected = config.id == selectedId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConfigSelect(config.id) }
                                        .padding(vertical = 12.dp, horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = config.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isSelected) colors.brandNorm else colors.textNorm,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (!config.isReadOnly) {
                                            Text(
                                                text = stringResource(R.string.custom_config),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textWeak
                                            )
                                        }
                                    }
                                    if (!config.isReadOnly) {
                                        IconButton(onClick = { editingProfile = config }) {
                                            Icon(ProtonIcons.Pen, contentDescription = stringResource(R.string.btn_edit), tint = colors.iconNorm)
                                        }
                                        IconButton(onClick = { onDelete(config.id) }) {
                                            Icon(ProtonIcons.Trash, contentDescription = stringResource(R.string.btn_delete), tint = colors.iconWeak)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.shade20.copy(alpha = 0.5f))

                        TextButton(
                            onClick = onCreateNew,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(ProtonIcons.Plus, contentDescription = null, tint = colors.brandNorm)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_create_new_config), color = colors.brandNorm)
                        }
                    }
                }
            }
        }
    }

    editingProfile?.let {
        ObfuscationProfileEditDialog(
            profile = it,
            onDismiss = { editingProfile = null },
            onSave = { updatedProfile ->
                onEdit(updatedProfile)
                editingProfile = null
            }
        )
    }
}

@Composable
fun ObfuscationProfileEditDialog(
    profile: ObfuscationProfile,
    onDismiss: () -> Unit,
    onSave: (ObfuscationProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var name by remember { mutableStateOf(profile.name) }
    var jc by remember { mutableIntStateOf(profile.jc) }
    var jmin by remember { mutableIntStateOf(profile.jmin) }
    var jmax by remember { mutableIntStateOf(profile.jmax) }
    var s1 by remember { mutableIntStateOf(profile.s1) }
    var s2 by remember { mutableIntStateOf(profile.s2) }
    var h1 by remember { mutableStateOf(profile.h1) }
    var h2 by remember { mutableStateOf(profile.h2) }
    var h3 by remember { mutableStateOf(profile.h3) }
    var h4 by remember { mutableStateOf(profile.h4) }
    var i1 by remember { mutableStateOf(profile.i1) }

    Box(modifier = modifier) {
        Dialog(onDismissRequest = onDismiss) {
            Box(modifier = Modifier) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                    ) {
                        Text(
                            text = stringResource(R.string.title_edit_obfuscation_config),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            modifier = Modifier.padding(24.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(contentType = "TextField") {
                                SmoothOutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text(stringResource(R.string.obfuscation_config_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.brandNorm,
                                        unfocusedBorderColor = colors.shade20
                                    )
                                )
                            }

                            // Junk
                            item(contentType = "JunkSettings") {
                                EditCategoryHeader(title = stringResource(R.string.obfuscation_category_junk))
                                EditSettingsCard {
                                    EditParamField(label = "Jc", value = jc.toString(), onValueChange = { jc = it.toIntOrNull() ?: 0 })
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.shade20.copy(alpha = 0.5f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        EditParamField(modifier = Modifier.weight(1f), label = "Jmin", value = jmin.toString(), onValueChange = { jmin = it.toIntOrNull() ?: 0 })
                                        EditParamField(modifier = Modifier.weight(1f), label = "Jmax", value = jmax.toString(), onValueChange = { jmax = it.toIntOrNull() ?: 0 })
                                    }
                                }
                            }

                            // Magic
                            item(contentType = "MagicSettings") {
                                EditCategoryHeader(title = stringResource(R.string.obfuscation_category_magic))
                                EditSettingsCard {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        EditParamField(modifier = Modifier.weight(1f), label = "S1", value = s1.toString(), onValueChange = { s1 = it.toIntOrNull() ?: 0 })
                                        EditParamField(modifier = Modifier.weight(1f), label = "S2", value = s2.toString(), onValueChange = { s2 = it.toIntOrNull() ?: 0 })
                                    }
                                }
                            }

                            // Headers
                            item(contentType = "HeaderSettings") {
                                EditCategoryHeader(title = stringResource(R.string.obfuscation_category_headers))
                                EditSettingsCard {
                                    Column {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            EditParamField(modifier = Modifier.weight(1f), label = "H1", value = h1, isNumeric = false, onValueChange = { h1 = it })
                                            EditParamField(modifier = Modifier.weight(1f), label = "H2", value = h2, isNumeric = false, onValueChange = { h2 = it })
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.shade20.copy(alpha = 0.5f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            EditParamField(modifier = Modifier.weight(1f), label = "H3", value = h3, isNumeric = false, onValueChange = { h3 = it })
                                            EditParamField(modifier = Modifier.weight(1f), label = "H4", value = h4, isNumeric = false, onValueChange = { h4 = it })
                                        }
                                    }
                                }
                            }

                            // Advanced (I1)
                            item(contentType = "AdvancedSettings") {
                                EditCategoryHeader(title = stringResource(R.string.obfuscation_category_advanced))
                                EditSettingsCard {
                                    EditParamField(label = "I1", value = i1, isNumeric = false, onValueChange = { i1 = it })
                                }
                            }

                            item(contentType = "Spacer") { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(android.R.string.cancel), color = colors.textWeak)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    onSave(profile.copy(
                                        name = name,
                                        jc = jc,
                                        jmin = jmin,
                                        jmax = jmax,
                                        s1 = s1,
                                        s2 = s2,
                                        h1 = h1,
                                        h2 = h2,
                                        h3 = h3,
                                        h4 = h4,
                                        i1 = i1
                                    ))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.btn_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCategoryHeader(title: String) {
    val colors = ProtonNextTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = colors.brandNorm,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EditSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun EditParamField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true
) {
    val colors = ProtonNextTheme.colors
    SmoothTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = colors.textNorm,
            unfocusedTextColor = colors.textNorm,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = colors.brandNorm,
            unfocusedIndicatorColor = colors.shade20,
            cursorColor = colors.brandNorm,
            focusedLabelColor = colors.brandNorm,
            unfocusedLabelColor = colors.textWeak
        ),
        singleLine = true
    )
}

/**
 * Live preview of the profile being edited - same visual language as
 * ProfileCardItem on the profiles screen (accent gradient by target).
 */
@Composable
private fun ProfileHeroPreview(
    name: String,
    subtitle: String,
    protocolLabel: String,
    portLabel: String,
    targetServerId: String?,
    targetCity: String?,
    targetCountry: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    val accent = remember(targetServerId, targetCity, targetCountry, colors.brandNorm) {
        getProfileAccent(targetServerId, targetCity, targetCountry, colors.brandNorm)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(28.dp), alpha = 0.4f, shadowElevation = 0.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.start.copy(alpha = 0.25f),
                        Color.Transparent,
                        accent.end.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp, 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.start.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                val flagResId = if (targetCountry != null) CountryUtils.getFlagResource(context, targetCountry) else 0
                when {
                    flagResId != 0 -> FlagIcon(countryFlag = flagResId, size = DpSize(72.dp, 48.dp))
                    targetCountry != null -> Text(
                        text = CountryUtils.getFlagForCountry(targetCountry),
                        style = MaterialTheme.typography.titleLarge
                    )
                    else -> FlagIcon(countryFlag = R.drawable.flag_fastest, size = DpSize(72.dp, 48.dp))
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureBadge(text = protocolLabel, accent = accent.start)
                    FeatureBadge(text = portLabel, accent = accent.start)
                }
            }
        }
    }
}
