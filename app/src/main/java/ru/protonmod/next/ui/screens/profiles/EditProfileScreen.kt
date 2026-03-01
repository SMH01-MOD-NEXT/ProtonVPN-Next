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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.screens.countries.CityDisplayItem
import ru.protonmod.next.ui.screens.countries.CountryDisplayItem
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.CountryUtils
import java.util.Locale
import java.util.UUID

// Helper function to dynamically localize city names based on string resources
// For example: "New York" -> looks for R.string.city_new_york
private fun getLocalizedCityName(context: Context, cityName: String): String {
    if (cityName.isBlank()) return cityName
    val resourceName = "city_${cityName.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")}"
    val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
    return if (resourceId != 0) context.getString(resourceId) else cityName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profileId: String?,
    viewModel: ProfilesViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors
    val profiles by viewModel.profiles.collectAsState()
    val countries by viewModel.countries.collectAsState()
    val customObfuscationConfigs by viewModel.customObfuscationConfigs.collectAsState()

    val editingProfile = remember(profileId, profiles) {
        profiles.find { it.id == profileId }
    }

    var profileName by remember { mutableStateOf("") }
    var targetCountry by remember { mutableStateOf<String?>(null) }
    var targetCity by remember { mutableStateOf<String?>(null) }
    var targetServerId by remember { mutableStateOf<String?>(null) }
    var targetServerName by remember { mutableStateOf<String?>(null) }
    var selectedProtocol by remember { mutableStateOf("AmneziaWG") }
    var selectedPort by remember { mutableIntStateOf(0) }
    var autoOpenUrl by remember { mutableStateOf("") }
    var obfuscationEnabled by remember { mutableStateOf(false) }
    var obfuscationProfileId by remember { mutableStateOf("standard_1") }

    // Update state when editingProfile is loaded
    LaunchedEffect(editingProfile) {
        editingProfile?.let {
            profileName = it.name
            targetCountry = it.targetCountry
            targetCity = it.targetCity
            targetServerId = it.targetServerId
            targetServerName = it.targetServerName
            selectedProtocol = it.protocol
            selectedPort = it.port
            autoOpenUrl = it.autoOpenUrl ?: ""
            obfuscationEnabled = it.isObfuscationEnabled
            obfuscationProfileId = it.obfuscationProfileId ?: "standard_1"
        }
    }

    var showLocationDialog by remember { mutableStateOf(false) }
    var showProtocolDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showObfuscationConfigDialog by remember { mutableStateOf(false) }

    val standardProfileName = stringResource(R.string.obfuscation_config_standard)

    Scaffold(
        containerColor = colors.backgroundNorm,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.backgroundNorm,
                    titleContentColor = colors.textNorm
                ),
                title = { Text(if (profileId == null) stringResource(R.string.title_create_profile) else stringResource(R.string.title_edit_profile)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textNorm)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (profileName.isBlank()) {
                                Toast.makeText(context, R.string.error_empty_profile_name, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val validatedUrl = if (autoOpenUrl.isNotBlank() && !autoOpenUrl.startsWith("http://") && !autoOpenUrl.startsWith("https://")) {
                                "https://$autoOpenUrl"
                            } else autoOpenUrl

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
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                    ) {
                        Text(stringResource(R.string.btn_save), color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Category(title = stringResource(R.string.category_general)) {
                    SettingTextFieldRow(
                        label = stringResource(R.string.label_profile_name),
                        value = profileName,
                        onValueChange = { profileName = it }
                    )
                }
            }

            item {
                Category(title = stringResource(R.string.category_connection)) {
                    val locationSubtitle = remember(targetCountry, targetCity, targetServerId, targetServerName) {
                        when {
                            targetServerId != null -> context.getString(R.string.location_server, targetServerName ?: targetServerId)
                            targetCity != null -> "🏙️ ${getLocalizedCityName(context, targetCity!!)}, ${CountryUtils.getCountryName(context, targetCountry)}"
                            targetCountry != null -> {
                                val flagEmoji = CountryUtils.getFlagForCountry(targetCountry)
                                val localizedName = CountryUtils.getCountryName(context, targetCountry)
                                "$flagEmoji $localizedName"
                            }
                            else -> context.getString(R.string.location_fastest)
                        }
                    }

                    SettingRowWithIcon(
                        icon = if (targetCountry == null) Icons.Rounded.Bolt else null,
                        countryCode = targetCountry,
                        title = stringResource(R.string.label_location),
                        subtitle = locationSubtitle,
                        onClick = { showLocationDialog = true }
                    )

                    SettingRowWithIcon(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.label_protocol),
                        subtitle = selectedProtocol,
                        onClick = { showProtocolDialog = true }
                    )

                    SettingRowWithIcon(
                        icon = Icons.Rounded.Power,
                        title = stringResource(R.string.label_port),
                        subtitle = (if (selectedPort == 0) stringResource(R.string.settings_port_auto) else selectedPort.toString()),
                        onClick = { showPortDialog = true }
                    )
                }
            }

            item {
                Category(title = stringResource(R.string.category_advanced)) {
                    SettingToggleRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.label_obfuscation),
                        subtitle = stringResource(R.string.obfuscation_desc),
                        checked = obfuscationEnabled,
                        onCheckedChange = { obfuscationEnabled = it }
                    )

                    if (obfuscationEnabled) {
                        val allConfigs = listOf(ObfuscationProfile.getStandardProfile(standardProfileName)) + customObfuscationConfigs
                        val selectedConfig = allConfigs.find { it.id == obfuscationProfileId } ?: allConfigs.first()

                        SettingRowWithIcon(
                            icon = Icons.Rounded.Settings,
                            title = stringResource(R.string.label_obfuscation_config),
                            subtitle = selectedConfig.name,
                            onClick = { showObfuscationConfigDialog = true },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            item {
                Category(title = stringResource(R.string.category_automation)) {
                    SettingRowWithIcon(
                        icon = Icons.Rounded.OpenInBrowser,
                        title = stringResource(R.string.label_connect_go_website),
                        subtitle = autoOpenUrl.ifEmpty { stringResource(R.string.label_not_configured) },
                        onClick = { showUrlDialog = true }
                    )
                }

                Text(
                    text = stringResource(R.string.automation_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
                )
            }

            if (profileId != null) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.deleteProfile(profileId)
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_delete), color = Color.Red)
                    }
                }
            }
        }
    }

    if (showPortDialog) {
        PortSelectionDialog(
            currentPort = selectedPort,
            onDismiss = { showPortDialog = false },
            onPortSelected = {
                selectedPort = it
                showPortDialog = false
            }
        )
    }

    if (showProtocolDialog) {
        ProtocolSelectionDialog(
            currentProtocol = selectedProtocol,
            onDismiss = { showProtocolDialog = false },
            onProtocolSelected = {
                selectedProtocol = it
                showProtocolDialog = false
            }
        )
    }

    if (showUrlDialog) {
        AutoOpenUrlDialog(
            currentUrl = autoOpenUrl,
            onDismiss = { showUrlDialog = false },
            onSave = {
                autoOpenUrl = it
                showUrlDialog = false
            }
        )
    }

    if (showLocationDialog) {
        LocationSelectionDialog(
            countries = countries,
            selectedCountry = targetCountry,
            selectedCity = targetCity,
            viewModel = viewModel,
            onDismiss = { showLocationDialog = false },
            onLocationSelected = { country, city, serverId, serverName ->
                targetCountry = country
                targetCity = city
                targetServerId = serverId
                targetServerName = serverName
                showLocationDialog = false
            }
        )
    }

    if (showObfuscationConfigDialog) {
        ObfuscationConfigSelectionDialog(
            configs = listOf(ObfuscationProfile.getStandardProfile(standardProfileName)) + customObfuscationConfigs,
            selectedId = obfuscationProfileId,
            onDismiss = { showObfuscationConfigDialog = false },
            onConfigSelected = {
                obfuscationProfileId = it
                showObfuscationConfigDialog = false
            },
            onCreateNew = {
                val newConfig = ObfuscationProfile.createDefaultCustomProfile(
                    id = UUID.randomUUID().toString(),
                    name = context.getString(R.string.custom_config_name, customObfuscationConfigs.size + 1)
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

// Reusable card for the selection dialog to match CountriesScreen styling
@Composable
fun SelectionCard(
    title: String,
    icon: @Composable () -> Unit,
    load: Int? = null,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)) // Lighter card on a darker background
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textNorm,
                    modifier = Modifier.weight(1f)
                )

                if (load != null) {
                    val loadColor = CountryUtils.getColorForLoad(load)
                    Surface(
                        color = loadColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$load%",
                            style = MaterialTheme.typography.labelMedium,
                            color = loadColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Premium Load Progress Bar at the bottom of the card
            if (load != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(colors.textNorm.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(load / 100f)
                            .fillMaxHeight()
                            .background(CountryUtils.getColorForLoad(load))
                    )
                }
            }
        }
    }
}

@Composable
fun LocationSelectionDialog(
    countries: List<CountryDisplayItem>,
    selectedCountry: String?,
    selectedCity: String?,
    viewModel: ProfilesViewModel,
    onDismiss: () -> Unit,
    onLocationSelected: (String?, String?, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val colors = ProtonNextTheme.colors
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) } // 0: Country, 1: City, 2: Server
    var currentCountry by remember { mutableStateOf(selectedCountry) }
    var currentCity by remember { mutableStateOf(selectedCity) }
    var isTransitioning by remember { mutableStateOf(false) }

    var cities by remember { mutableStateOf<List<CityDisplayItem>>(emptyList()) }
    var servers by remember { mutableStateOf<List<LogicalServer>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundNorm) // Darker dialog background
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth().fillMaxHeight(0.7f)
            ) {
                Text(
                    text = when(step) {
                        0 -> stringResource(R.string.title_select_country)
                        1 -> stringResource(R.string.title_select_city)
                        else -> stringResource(R.string.title_select_server)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                AnimatedContent(
                    targetState = step,
                    label = "location_step",
                    modifier = Modifier.weight(1f)
                ) { currentStep ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SelectionCard(
                                title = when(currentStep) {
                                    0 -> stringResource(R.string.location_fastest)
                                    1 -> stringResource(R.string.location_fastest_in_country, CountryUtils.getCountryName(context, currentCountry))
                                    else -> stringResource(R.string.location_fastest_in_city, getLocalizedCityName(context, currentCity ?: ""))
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.brandNorm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    if (!isTransitioning) {
                                        when(currentStep) {
                                            0 -> onLocationSelected(null, null, null, null)
                                            1 -> onLocationSelected(currentCountry, null, null, null)
                                            2 -> onLocationSelected(currentCountry, currentCity, null, null)
                                        }
                                    }
                                }
                            )
                        }

                        when (currentStep) {
                            0 -> {
                                items(countries) { countryItem ->
                                    val localizedName = CountryUtils.getCountryName(context, countryItem.code)
                                    val flagResId = CountryUtils.getFlagResource(context, countryItem.code)
                                    SelectionCard(
                                        title = localizedName,
                                        icon = {
                                            if (flagResId != 0) {
                                                Image(
                                                    painter = painterResource(id = flagResId),
                                                    contentDescription = localizedName,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.FillBounds
                                                )
                                            } else {
                                                Text(
                                                    text = CountryUtils.getFlagForCountry(countryItem.code),
                                                    fontSize = 24.sp
                                                )
                                            }
                                        },
                                        load = countryItem.averageLoad,
                                        onClick = {
                                            if (!isTransitioning) {
                                                scope.launch {
                                                    isTransitioning = true
                                                    cities = viewModel.getCitiesForCountry(countryItem.code)
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
                                items(cities) { cityItem ->
                                    val localizedCityName = getLocalizedCityName(context, cityItem.name)
                                    SelectionCard(
                                        title = localizedCityName,
                                        icon = {
                                            Text(text = "🏙️", fontSize = 24.sp)
                                        },
                                        load = cityItem.averageLoad,
                                        onClick = {
                                            if (!isTransitioning) {
                                                scope.launch {
                                                    isTransitioning = true
                                                    servers = viewModel.getServersForCity(currentCountry!!, cityItem.name)
                                                    currentCity = cityItem.name
                                                    step = 2
                                                    isTransitioning = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            else -> {
                                items(servers) { server ->
                                    SelectionCard(
                                        title = server.name,
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(colors.backgroundNorm), // Match CountriesScreen styling
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Public,
                                                    contentDescription = null,
                                                    tint = colors.iconNorm,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        load = server.averageLoad,
                                        onClick = {
                                            if (!isTransitioning) {
                                                onLocationSelected(currentCountry, currentCity, server.id, server.name)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        TextButton(onClick = { step-- }, enabled = !isTransitioning) {
                            Text(stringResource(R.string.desc_back_button), color = colors.brandNorm)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    TextButton(onClick = onDismiss, enabled = !isTransitioning) {
                        Text(stringResource(android.R.string.cancel), color = colors.brandNorm)
                    }
                }
            }
        }
    }
}

@Composable
fun Category(
    modifier: Modifier = Modifier,
    title: String,
    content: (@Composable ColumnScope.() -> Unit),
) {
    val colors = ProtonNextTheme.colors
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textNorm,
            modifier = modifier
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun SettingRowWithIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    countryCode: String? = null,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    var baseModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        baseModifier = baseModifier.clickable(onClick = onClick)
    }
    baseModifier = baseModifier.padding(vertical = 12.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || countryCode != null) {
            val isBolt = icon == Icons.Rounded.Bolt
            val flagResId = CountryUtils.getFlagResource(context, countryCode)

            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isBolt) colors.brandNorm else colors.brandNorm.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (flagResId != 0) {
                    Image(
                        painter = painterResource(id = flagResId),
                        contentDescription = countryCode,
                        modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.FillBounds
                    )
                } else if (countryCode != null) {
                    Text(text = CountryUtils.getFlagForCountry(countryCode), style = MaterialTheme.typography.titleMedium)
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isBolt) Color.White else colors.brandNorm,
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
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ProtonNextTheme.colors
    SettingRowWithIcon(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
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
    onValueChange: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
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
fun PortSelectionDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onPortSelected: (Int) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val portOptions = listOf(0, 443, 123, 1194, 51820)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.label_port),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(portOptions) { port ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPortSelected(port) }
                                .padding(vertical = 12.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (port == 0) stringResource(R.string.settings_port_auto) else port.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textNorm,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = (port == currentPort),
                                onClick = { onPortSelected(port) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) {
                    Text(stringResource(id = android.R.string.cancel), color = colors.brandNorm)
                }
            }
        }
    }
}

@Composable
fun ProtocolSelectionDialog(
    currentProtocol: String,
    onDismiss: () -> Unit,
    onProtocolSelected: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val protocols = listOf("AmneziaWG")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_select_protocol),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(protocols) { protocol ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProtocolSelected(protocol) }
                                .padding(vertical = 12.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = protocol,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textNorm,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = (protocol == currentProtocol),
                                onClick = { onProtocolSelected(protocol) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) {
                    Text(stringResource(id = android.R.string.cancel), color = colors.brandNorm)
                }
            }
        }
    }
}

@Composable
fun AutoOpenUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var url by remember { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.title_auto_open_url),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.label_enter_url)) },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brandNorm,
                        unfocusedBorderColor = colors.shade20
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel), color = colors.textWeak)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(url) },
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


@Composable
fun ObfuscationConfigSelectionDialog(
    configs: List<ObfuscationProfile>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onConfigSelected: (String) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (ObfuscationProfile) -> Unit,
    onDelete: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var editingProfile by remember { mutableStateOf<ObfuscationProfile?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
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
                    items(configs) { config ->
                        val isSelected = config.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfigSelected(config.id) }
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
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.btn_edit), tint = colors.iconNorm)
                                }
                                IconButton(onClick = { onDelete(config.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.btn_delete), tint = colors.iconWeak)
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
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.brandNorm)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_create_new_config), color = colors.brandNorm)
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
    onSave: (ObfuscationProfile) -> Unit
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
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
                    item {
                        OutlinedTextField(
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
                    item {
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
                    item {
                        EditCategoryHeader(title = stringResource(R.string.obfuscation_category_magic))
                        EditSettingsCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EditParamField(modifier = Modifier.weight(1f), label = "S1", value = s1.toString(), onValueChange = { s1 = it.toIntOrNull() ?: 0 })
                                EditParamField(modifier = Modifier.weight(1f), label = "S2", value = s2.toString(), onValueChange = { s2 = it.toIntOrNull() ?: 0 })
                            }
                        }
                    }

                    // Headers
                    item {
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
                    item {
                        EditCategoryHeader(title = stringResource(R.string.obfuscation_category_advanced))
                        EditSettingsCard {
                            EditParamField(label = "I1", value = i1, isNumeric = false, onValueChange = { i1 = it })
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
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
    val colors = ProtonNextTheme.colors
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun EditParamField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isNumeric: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    TextField(
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