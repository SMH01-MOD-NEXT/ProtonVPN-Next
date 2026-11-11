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

import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import ru.protonmod.next.di.ApplicationScope
import kotlinx.coroutines.withContext
import ru.protonmod.next.vpn.VpnTunnelState
import ru.protonmod.next.R
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.repository.ServerProbeRepository
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class CountryDisplayItem(val code: String, val averageLoad: Int)
data class CityDisplayItem(val name: String, val localizedName: String, val averageLoad: Int)

enum class CountryConnectionMode { STANDARD, TOR }

sealed class BottomSheetContent {
    data class Cities(
        val countryCode: String,
        val cities: List<CityDisplayItem>
    ) : BottomSheetContent()

    data class Servers(
        val countryCode: String,
        val cityName: String,
        val localizedCityName: String,
        val servers: List<LogicalServer>
    ) : BottomSheetContent()
}

sealed class CountriesUiState {
    data object Loading : CountriesUiState()
    data class Success(
        val countries: List<CountryDisplayItem>,
        val bottomSheetContent: BottomSheetContent? = null,
        val loadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
        val isBottomSheetOpen: Boolean = false,
        val connectionMode: CountryConnectionMode = CountryConnectionMode.STANDARD,
        val availability: AvailabilityBadges = AvailabilityBadges.EMPTY
    ) : CountriesUiState()
    data class Error(val message: String) : CountriesUiState()
}

sealed class NavigationState {
    data object Countries : NavigationState()
    data class Cities(val countryCode: String) : NavigationState()
    data class Servers(val countryCode: String, val cityName: String) : NavigationState()
}

@HiltViewModel
class CountriesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val settingsManager: SettingsManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val serverProbeRepository: ServerProbeRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val customRepository: ru.protonmod.next.data.repository.CustomConfigRepository,
    private val warpRepository: ru.protonmod.next.data.repository.WarpRepository,

) : ViewModel() {

    companion object {
        private const val TAG = "CountriesViewModel"
    }

    private val _navState = MutableStateFlow<NavigationState>(NavigationState.Countries)
    private val _error = MutableStateFlow<String?>(null)

    // Memoized countries list to prevent unnecessary instance changes and recalculations.
    // Use distinctUntilChanged to only emit when the content actually changes (including loads).
    private val _countries = vpnRepository.getServersFlow()
        .map { servers ->
            servers.groupBy { it.exitCountry }
                .map { (code, countryServers) ->
                    val avg = if (countryServers.isEmpty()) 0 else countryServers.map { it.averageLoad }.average().toInt()
                    CountryDisplayItem(code, avg)
                }
                .sortedBy { it.code }
        }
        .distinctUntilChanged()

    // Server scanner verdicts for the badges. They are built off the main thread because
    // a running scan publishes new results twice per second.
    private val availabilityBadges = combine(
        vpnRepository.getServersFlow(),
        serverProbeRepository.results,
        sessionDao.getSessionFlow().map { it?.userTier ?: 0 }.distinctUntilChanged(),
    ) { servers, results, userTier ->
        AvailabilityBadges.build(servers, results, userTier)
    }
        .distinctUntilChanged()
        .flowOn(dispatcherProvider.default())

    val uiState: StateFlow<CountriesUiState> = combine(
        combine(_countries, vpnRepository.getServersFlow(), _navState) { c, s, n -> Triple(c, s, n) },
        vpnRepository.isUpdating,
        combine(settingsManager.serverLoadDisplayMode, settingsManager.torModeEnabled, _error) { loadMode, torEnabled, error ->
            Triple(loadMode, torEnabled, error)
        },
        availabilityBadges,
    ) { (countries, servers, nav), isUpdating, modeState, availability ->
        val (loadMode, torEnabled, error) = modeState
        if (isUpdating && servers.isEmpty()) {
            return@combine CountriesUiState.Loading
        }
        if (error != null && servers.isEmpty()) {
            return@combine CountriesUiState.Error(error)
        }

        val bottomSheetContent = when (nav) {
            is NavigationState.Countries -> null
            is NavigationState.Cities -> {
                val cities = servers.filter { it.exitCountry == nav.countryCode }
                    .groupBy { it.city }
                    .map { (name, cityServers) ->
                        val avg = if (cityServers.isEmpty()) 0 else cityServers.map { it.averageLoad }.average().toInt()
                        val localizedName = cityServers.firstOrNull()?.localizedCity ?: name
                        CityDisplayItem(name, localizedName, avg)
                    }
                    .sortedBy { it.localizedName }
                BottomSheetContent.Cities(nav.countryCode, cities)
            }
            is NavigationState.Servers -> {
                val cityServers = servers.filter { it.exitCountry == nav.countryCode && it.city == nav.cityName }
                    .sortedBy { it.name }
                val localizedCityName = cityServers.firstOrNull()?.localizedCity ?: nav.cityName
                BottomSheetContent.Servers(nav.countryCode, nav.cityName, localizedCityName, cityServers)
            }
        }

        CountriesUiState.Success(
            countries = countries,
            bottomSheetContent = bottomSheetContent,
            loadDisplayMode = loadMode,
            isBottomSheetOpen = nav != NavigationState.Countries,
            connectionMode = if (torEnabled) CountryConnectionMode.TOR else CountryConnectionMode.STANDARD,
            availability = availability,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountriesUiState.Loading)

    val connectedServer: StateFlow<LogicalServer?> = connectedServerState.connectedServer

    val customConfigs = customRepository.configs
    val customBusy = MutableStateFlow(false)
    val customMessage = MutableStateFlow<Int?>(null)
    init {
        viewModelScope.launch { customAction { customRepository.load() } }
        initialFetch()
    }

    private suspend fun customAction(block: suspend () -> Unit) {
        if (customBusy.value) return
        customBusy.value = true
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { customMessage.value = R.string.custom_error }
        finally { customBusy.value = false }
    }
    fun saveCustom(id: String?, name: String, raw: String, country: String, city: String) {
        viewModelScope.launch { customAction {
            val link = withContext(dispatcherProvider.io()) { customRepository.normalize(raw) }
            var resolvedCountry = country.trim().uppercase(java.util.Locale.ROOT)
            var resolvedCity = city.trim()
            if (resolvedCountry.isEmpty() || resolvedCity.isEmpty()) {
                val location = locateCustom(link)
                if (resolvedCountry.isEmpty()) resolvedCountry = location.first
                if (resolvedCity.isEmpty()) resolvedCity = location.second
            }
            customRepository.save(ru.protonmod.next.data.repository.CustomConfig(
                id = id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), link = link,
                country = resolvedCountry, city = resolvedCity))
        } }
    }
    private suspend fun locateCustom(link: String): Pair<String, String> = withContext(dispatcherProvider.io()) {
        try {
            // Only the endpoint address is disclosed to the geolocation provider, never keys.
            val host = ru.protonmod.next.vpn.ProxyLinkParser.inspectLink(link).server
            val ip = java.net.InetAddress.getAllByName(host).firstOrNull { !it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isAnyLocalAddress }
                ?: return@withContext "" to ""
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("freeipapi.com").addPathSegments("api/json").addPathSegment(ip.hostAddress!!).build()
            val client = okhttp3.OkHttpClient.Builder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
            client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext "" to ""
                val data = kotlinx.serialization.json.Json.parseToJsonElement(response.body.string()) as kotlinx.serialization.json.JsonObject
                fun text(key: String) = (data[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                val code = text("countryCode").uppercase(java.util.Locale.ROOT).takeIf { it in java.util.Locale.getISOCountries() }.orEmpty()
                code to text("cityName").take(100)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { "" to "" }
    }
    fun generateWarp(endpoint: String? = null, port: Int? = null, awg2: Boolean = false) { viewModelScope.launch { customAction {
        val text = warpRepository.generate(endpoint, port, awg2)
        val link = customRepository.normalize(text)
        val location = locateCustom(link)
        customRepository.save(ru.protonmod.next.data.repository.CustomConfig(name = "WARP", link = link, country = location.first, city = location.second))
    } } }
    fun deleteCustom(id: String) { viewModelScope.launch { customAction { customRepository.delete(id) } } }
    fun useCustomAsHop(config: ru.protonmod.next.data.repository.CustomConfig) { viewModelScope.launch { customAction {
        val previous = settingsManager.proxyChainConfig.first().trim()
        val links = previous.lineSequence().filter(String::isNotBlank).toList()
        require(links.size < 4 && "custom://${config.id}" !in links)
        settingsManager.setProxyChainConfig((links + "custom://${config.id}").joinToString("\n"))
        settingsManager.setProxyChainEnabled(true)
        customMessage.value = R.string.custom_hop_added
    } } }
    fun connectCustom(config: ru.protonmod.next.data.repository.CustomConfig) {
        applicationScope.launch {
            val info = ru.protonmod.next.vpn.ProxyLinkParser.inspectLink(config.link)
            val physical = ru.protonmod.next.data.network.PhysicalServer(config.id, domain = info.server, status = 1)
            val logical = LogicalServer("custom:${config.id}", config.name, 0, 0, config.country, config.country, config.city, listOf(physical))
            val session = sessionDao.getSession() ?: ru.protonmod.next.data.local.SessionEntity(accessToken = "", refreshToken = "", sessionId = "", userId = "")
            if (amneziaVpnManager.tunnelState.value == VpnTunnelState.UP || amneziaVpnManager.isConnecting.value)
                amneziaVpnManager.reconnect(logical.id, physical, session, logicalServer = logical)
            else amneziaVpnManager.connect(logical.id, physical, session, logicalServer = logical)
        }
    }

    private fun initialFetch() {
        viewModelScope.launch {
            _error.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _error.value = context.getString(R.string.error_session_not_found)
                return@launch
            }
            vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier, forceRefresh = false)
                .onFailure { _error.value = it.localizedMessage }
        }
    }

    fun loadServers() {
        initialFetch()
    }

    private suspend fun connectToServer(server: LogicalServer) {
        // Reliable server selection: Fallback to any server with min load if status == 1 is absent.
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load }

        if (physicalServer != null) {
            connectedServerState.setConnectedServer(server)
            val tunnelState = amneziaVpnManager.tunnelState.value
            val isConnecting = amneziaVpnManager.isConnecting.value
            // Re-fetch the session right before use so we always pass the freshest credentials,
            // even if a token refresh occurred between server selection and the actual connect call.
            val session = sessionDao.getSession()
            if (session == null) {
                ProtonLogger.e(TAG, "Cannot connect: No session found")
                return
            }
            if (tunnelState == VpnTunnelState.UP || isConnecting) {
                amneziaVpnManager.reconnect(server.id, physicalServer, session)
            } else {
                amneziaVpnManager.connect(server.id, physicalServer, session)
            }
        } else {
            _error.value = context.getString(R.string.label_server_unavailable)
        }
    }

    // Adapted from drxekus/0c7543e: navigation must not cancel the connection.
    fun selectCountry(country: String) {
        applicationScope.launch {
            val servers = vpnRepository.getCachedServers()
            val serversInCountry = servers.filter { it.exitCountry == country }
            if (serversInCountry.isNotEmpty()) {
                val bestServer = serversInCountry
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad } 
                    ?: serversInCountry.minByOrNull { it.averageLoad }
                
                bestServer?.let { connectToServer(it) }
            }
        }
    }

    fun expandCitiesForCountry(country: String) {
        _navState.value = NavigationState.Cities(country)
    }

    fun backToCountries() {
        _navState.value = NavigationState.Countries
    }

    fun selectCity(city: String) {
        val nav = _navState.value
        if (nav !is NavigationState.Cities) return
        applicationScope.launch {

            val servers = vpnRepository.getCachedServers()
            val serversInCity = servers.filter { it.exitCountry == nav.countryCode && it.city == city }
            if (serversInCity.isNotEmpty()) {
                val bestServer = serversInCity
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad }
                    ?: serversInCity.minByOrNull { it.averageLoad }
                    
                bestServer?.let { connectToServer(it) }
            }
        }
    }

    fun expandServersForCity(city: String) {
        val nav = _navState.value
        if (nav is NavigationState.Cities) {
            _navState.value = NavigationState.Servers(nav.countryCode, city)
        }
    }

    fun backToCities() {
        val nav = _navState.value
        if (nav is NavigationState.Servers) {
            _navState.value = NavigationState.Cities(nav.countryCode)
        }
    }

    // Adapted from drxekus/0c7543e: navigation must not cancel the connection.
    fun selectServer(server: LogicalServer) {
        applicationScope.launch {
            connectToServer(server)
        }
    }

    fun setConnectionMode(mode: CountryConnectionMode) {
        viewModelScope.launch {
            settingsManager.setTorModeEnabled(mode == CountryConnectionMode.TOR)
        }
    }
}
