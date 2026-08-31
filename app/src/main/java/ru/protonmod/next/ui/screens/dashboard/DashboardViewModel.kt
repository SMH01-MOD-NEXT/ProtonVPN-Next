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

package ru.protonmod.next.ui.screens.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.vpn.VpnTunnelState
import org.json.JSONObject
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import kotlinx.collections.immutable.toImmutableList
import ru.protonmod.next.data.local.TrafficStatsDao
import ru.protonmod.next.data.local.TrafficStatsEntity
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.netshield.NetShieldStats
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.ip.IpEchoSources
import ru.protonmod.next.utils.RegionUtils
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.VpnAutomationManager
import ru.protonmod.next.utils.system.SystemUtils
import java.net.Proxy
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class LocationText(
    val country: String,
    val countryCode: String? = null,
    val ip: String,
)

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val servers: List<LogicalServer>,
        val recentConnections: List<LogicalServer> = emptyList(),
        val profiles: List<VpnProfileEntity> = emptyList(),
        val quickConnectStrategy: String = "fastest",
        val quickConnectTargetId: String? = null,
        val isConnected: Boolean = false,
        val connectedServer: LogicalServer? = null,
        val isConnecting: Boolean = false,
        val vpnState: AmneziaVpnManager.VpnState = AmneziaVpnManager.VpnState.DISCONNECTED,
        val certificateState: AmneziaVpnManager.CertificateState = AmneziaVpnManager.CertificateState.Valid,
        val originalLocationText: LocationText? = null,
        val vpnLocationText: LocationText? = null,
        val isIpHidden: Boolean = false,
        val serverLoadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
        val speed: String? = null,
        val trafficRx: String? = null,
        val trafficTx: String? = null,
        val isBatteryOptimized: Boolean = false,
        val connectionWarning: AmneziaVpnManager.ConnectionWarning? = null,
        val pauseEndTime: Long = 0,
        val netShieldLevel: NetShieldLevel = NetShieldLevel.DISABLED,
        val netShieldStats: NetShieldStats = NetShieldStats()
    ) : DashboardUiState()
    data class Error(val message: String, val isSessionError: Boolean = false) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val vpnAutomationManager: VpnAutomationManager,
    private val connectedServerState: ConnectedServerState,
    private val profileDao: ProfileDao,
    private val recentConnectionDao: ru.protonmod.next.data.local.RecentConnectionDao,
    private val trafficStatsDao: TrafficStatsDao,
    private val localNetShield: LocalNetShield
) : ViewModel() {

    // Shared OkHttpClient instances — created once, reused for every IP fetch, shut down in onCleared().
    // noProxyClient forces requests outside any system proxy so we always see the device's real IP.
    private val noProxyClient: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // defaultClient lets requests travel through whatever route is active (i.e. the VPN tunnel).
    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _errorMessage = MutableStateFlow<String?>(null)

    // Store original unprotected location
    private val _originalLocationText = MutableStateFlow<LocationText?>(null)
    // Store the secure VPN location (fetched after connection)
    private val _vpnLocationText = MutableStateFlow<LocationText?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        vpnRepository.getServersFlow(),
        vpnRepository.isUpdating,
        _errorMessage,
        amneziaVpnManager.vpnState,
        amneziaVpnManager.certState,
        connectedServerState.connectedServer,
        recentConnectionDao.getRecentConnections(),
        profileDao.getAllProfilesFlow(),
        settingsManager.quickConnectStrategy,
        settingsManager.quickConnectTargetId,
        settingsManager.serverLoadDisplayMode,
        _originalLocationText,
        _vpnLocationText,
        settingsManager.isIpHidden,
        amneziaVpnManager.speed,
        amneziaVpnManager.trafficRx,
        amneziaVpnManager.trafficTx,
        amneziaVpnManager.connectionWarning,
        settingsManager.pauseEndTime,
        settingsManager.netShieldLevel,
        localNetShield.stats
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val servers = args[0] as List<LogicalServer>
        val isUpdating = args[1] as Boolean
        val error = args[2] as String?
        val vpnState = args[3] as AmneziaVpnManager.VpnState
        val certState = args[4] as AmneziaVpnManager.CertificateState
        val connectedServer = args[5] as LogicalServer?
        @Suppress("UNCHECKED_CAST")
        val recentEntities = args[6] as List<RecentConnectionEntity>
        @Suppress("UNCHECKED_CAST")
        val profiles = args[7] as List<VpnProfileEntity>
        val qcStrategy = args[8] as String
        val qcTargetId = args[9] as String?
        val loadMode = args[10] as ServerLoadDisplayMode
        val originalLocationText = args[11] as LocationText?
        val vpnLocationText = args[12] as LocationText?
        val isIpHidden = args[13] as Boolean
        val speed = args[14] as String?
        val trafficRx = args[15] as String?
        val trafficTx = args[16] as String?
        val connectionWarning = args[17] as AmneziaVpnManager.ConnectionWarning?
        val pauseEndTime = args[18] as Long
        val netShieldLevel = args[19] as NetShieldLevel
        val netShieldStats = args[20] as NetShieldStats

        if (isUpdating && servers.isEmpty()) {
            DashboardUiState.Loading
        } else if (error != null && servers.isEmpty()) {
            DashboardUiState.Error(error)
        } else {
            val isConnected = vpnState == AmneziaVpnManager.VpnState.CONNECTED
            val isConnecting = vpnState == AmneziaVpnManager.VpnState.CONNECTING || vpnState == AmneziaVpnManager.VpnState.VERIFYING

            val recentServers = recentEntities.mapNotNull { entity ->
                servers.find { it.id == entity.serverId }
            }

            // System calls can sometimes hang or fail in test environments
            val isBatteryOptimized = runCatching {
                !SystemUtils.isIgnoringBatteryOptimizations(context)
            }.getOrDefault(false)

            DashboardUiState.Success(
                servers = servers,
                recentConnections = recentServers,
                profiles = profiles,
                quickConnectStrategy = qcStrategy,
                quickConnectTargetId = qcTargetId,
                isConnected = isConnected,
                connectedServer = connectedServer,
                isConnecting = isConnecting,
                vpnState = vpnState,
                certificateState = certState,
                originalLocationText = originalLocationText,
                vpnLocationText = vpnLocationText,
                isIpHidden = isIpHidden,
                serverLoadDisplayMode = loadMode,
                speed = speed,
                trafficRx = trafficRx,
                trafficTx = trafficTx,
                isBatteryOptimized = isBatteryOptimized,
                connectionWarning = connectionWarning,
                pauseEndTime = pauseEndTime,
                netShieldLevel = netShieldLevel,
                netShieldStats = netShieldStats
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    private var hasAttemptedAutoConnect = false

    init {
        loadServers()
        fetchOriginalLocation()

        viewModelScope.launch {
            val autoConnect = settingsManager.autoConnectEnabled.first()
            if (autoConnect && !hasAttemptedAutoConnect) {
                uiState.first { it is DashboardUiState.Success && it.servers.isNotEmpty() }
                val currentState = uiState.value as? DashboardUiState.Success
                
                val pauseEndTime = settingsManager.pauseEndTime.first()
                val isPaused = pauseEndTime > System.currentTimeMillis()

                if (currentState != null && !currentState.isConnected && !currentState.isConnecting && !isPaused) {
                    hasAttemptedAutoConnect = true
                    quickConnect()
                }
            }
        }

        // Global listener: Any time the VPN starts connecting (even from other screens), clear the old IP.
        viewModelScope.launch {
            amneziaVpnManager.isConnecting.collect { isConnecting ->
                if (isConnecting) {
                    _vpnLocationText.value = null
                }
            }
        }

        // Use collectLatest on both vpnState AND connectedServer.
        // This ensures that if the server changes while already connected, we restart the delay and fetch the new IP.
        viewModelScope.launch {
            combine(
                amneziaVpnManager.vpnState,
                connectedServerState.connectedServer
            ) { state, server ->
                Pair(state, server)
            }.collectLatest { (state, server) ->
                if (state == AmneziaVpnManager.VpnState.CONNECTED && server != null) {
                    // Give the tunnel 1 second to stabilize routing before starting fetch attempts
                    delay(1000)

                    recentConnectionDao.addRecentConnection(
                        RecentConnectionEntity(
                            serverId = server.id,
                            serverName = server.name,
                            city = server.city,
                            country = server.exitCountry,
                            lastConnectedAt = System.currentTimeMillis()
                        )
                    )
                    // Fetch the new secure IP of the VPN server
                    fetchVpnLocation(server.exitCountry)

                    // Refresh server loads after connection is established
                    loadServers()
                } else if (state == AmneziaVpnManager.VpnState.DISCONNECTED) {
                    _vpnLocationText.value = null
                }
            }
        }
    }

    fun toggleIpVisibility() {
        ProtonLogger.action("Dashboard", "User toggled IP visibility")
        viewModelScope.launch {
            val currentValue = (uiState.value as? DashboardUiState.Success)?.isIpHidden ?: false
            settingsManager.setIpHidden(!currentValue)
        }
    }

    private fun fetchOriginalLocation() {
        viewModelScope.launch {
            val location = fetchRealLocation()
            if (location != null) {
                val cleanCode = location.countryCode.trim().uppercase()
                // An address with no country is still the answer the user asked
                // for. Naming a country we could not resolve — this defaulted to
                // "US" — presents a guess as a fact, so it stays visibly unknown.
                val localizedCountry = if (cleanCode.isEmpty()) {
                    context.getString(R.string.unknown)
                } else {
                    CountryUtils.getCountryName(context, cleanCode).ifBlank { cleanCode }
                }
                _originalLocationText.value =
                    LocationText(localizedCountry, cleanCode.ifBlank { null }, location.ip)
            } else {
                // Fallback if API completely fails on boot
                val unknown = context.getString(R.string.unknown)
                _originalLocationText.value = LocationText(unknown, null, unknown)
            }
        }
    }

    private fun fetchVpnLocation(countryCode: String) {
        viewModelScope.launch {
            val unknown = context.getString(R.string.unknown)
            val originalIp = _originalLocationText.value?.ip

            // Clear connection pool to ensure we don't reuse a pre-VPN connection.
            withContext(Dispatchers.IO) {
                defaultClient.connectionPool.evictAll()
            }

            var location: LocationData? = null
            
            // Try up to 3 cycles to get an IP that is NOT the original one (handling routing lag)
            for (cycle in 1..3) {
                location = fetchRealLocation(bypassVpn = false)
                
                if (location != null) {
                    // If we got an IP and it's different from original (or original is unknown) - success
                    if (location.ip != originalIp || originalIp == unknown) {
                        break 
                    } else {
                        ProtonLogger.d("DashboardVM", "Leak detected: fetched IP matches original (cycle $cycle). Waiting...")
                    }
                }
                
                if (cycle < 3) {
                    delay(2000)
                    withContext(Dispatchers.IO) { defaultClient.connectionPool.evictAll() }
                }
            }

            // Prioritize API country code if valid, otherwise use the server's declared country code
            val apiCountryCode = location?.countryCode?.trim()?.uppercase()?.ifBlank { null }
            val fallbackCountryCode = countryCode.trim().uppercase().ifBlank { "US" }
            val finalCountryCode = apiCountryCode ?: fallbackCountryCode

            val localizedCountry = CountryUtils.getCountryName(context, finalCountryCode)
                .ifBlank { finalCountryCode }

            // If API failed to fetch IP after all retries, use "Unknown"
            val safeIp = location?.ip?.ifBlank { null } ?: unknown
            
            // If IP is unknown, country name and code should also be unknown/null for honesty as in official app
            val (finalCountryName, finalSafeCountryCode) = if (location?.ip.isNullOrBlank()) {
                unknown to null
            } else {
                localizedCountry to finalCountryCode
            }

            // Guard against race condition: check if tunnel is still active before updating UI
            if (amneziaVpnManager.tunnelState.value == VpnTunnelState.UP) {
                _vpnLocationText.value = LocationText(finalCountryName, finalSafeCountryCode, safeIp)
            }
        }
    }

    /**
     * Fetches the user's real location based on IP.
     *
     * @param bypassVpn If true, attempts to bypass the VPN tunnel (used for original IP).
     * @return [LocationData] object containing location info, or null in case of an error.
     */
    /**
     * The address this device is seen at, and the country it belongs to.
     *
     * Asks this project's own deployments first; see [IpEchoSources] for the
     * order and for why the two public services are last.
     *
     * @param bypassVpn when true the request is bound to a physical interface,
     *   so the answer is the device's real address rather than the tunnel's.
     * @return null only when every source failed.
     */
    private suspend fun fetchRealLocation(bypassVpn: Boolean = true): LocationData? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // CRITICAL FIX: To truly bypass the VPN tunnel on Android, we must bind the socket
        // to a physical network interface (WiFi or Cellular). Proxy.NO_PROXY only affects
        // HTTP proxies, not the routing table / TUN interface.
        val client = if (bypassVpn) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork?.takeIf { activeNetwork ->
                val caps = cm.getNetworkCapabilities(activeNetwork)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
            }

            if (network != null) {
                noProxyClient.newBuilder()
                    .socketFactory(network.socketFactory)
                    .build()
            } else {
                noProxyClient
            }
        } else {
            defaultClient
        }

        val russian = RegionUtils.isRussianRegion()
        val sources = IpEchoSources.ordered(
            isRussianRegion = russian,
            eventBypassUrl = settingsManager.getEventBypassUrlSync(),
        )

        for (source in sources) {
            // Our own deployments earn a second try. A service that is blocked,
            // or that refuses this traffic on purpose, refuses it just as fast
            // the third time — the old three-attempt loop over three such
            // services is what turned a failure into a twenty-second wait.
            val attempts = if (source.isOwn) 2 else 1

            for (attempt in 1..attempts) {
                try {
                    val request = Request.Builder().url(source.url).build()
                    val found = client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body.string()
                        if (body.isBlank()) return@use null
                        readLocation(body)
                    }

                    if (found != null) {
                        val located = ensureCountry(found, client, source, russian)
                        val duration = System.currentTimeMillis() - startTime
                        ProtonLogger.recordDistribution("location_fetch_latency", duration.toDouble())
                        ProtonLogger.recordCount("location_fetch_success", 1.0)
                        ProtonLogger.d("DashboardVM", "Location resolved by ${source.id}")

                        return@withContext located
                    }
                } catch (e: Exception) {
                    // Named by source, not by URL: a log that reads
                    // "[URL_REDACTED] timed out" cannot tell anyone which
                    // deployment to go and look at.
                    ProtonLogger.w("DashboardVM", "Fetch failed from ${source.id} (attempt $attempt): ${e.message}")
                }
                if (attempt < attempts) delay(1000)
            }
        }

        // Metrics
        ProtonLogger.recordCount("location_fetch_error", 1.0)
        null
    }

    /**
     * Reads an address out of whichever shape a source answers in.
     *
     * A missing country is not a failure. The Deno deployment has no country
     * signal at all, and demanding one before accepting its answer is what left
     * the dashboard with no address to show even when a mirror had replied.
     *
     * The country is taken from the first field that actually holds a two-letter
     * code, because the keys collide across sources: our own deployments put the
     * code in `country`, while one public service puts the country's full name
     * there and the code in `cc`.
     */
    private fun readLocation(body: String): LocationData? {
        val json = JSONObject(body)

        val ip = IpEchoSources.normaliseAddress(
            json.optString("ip")
                .ifBlank { json.optString("ipAddress") }
                .ifBlank { json.optString("query") }
        )
        if (ip.isBlank()) return null

        val code = listOf(
            json.optString("country"),
            json.optString("cc"),
            json.optString("countryCode"),
            json.optString("country_code"),
        ).map { it.trim().uppercase() }
            .firstOrNull { candidate -> candidate.length == 2 && candidate.all { it in 'A'..'Z' } }
            .orEmpty()

        // XX and T1 are placeholders for "unknown" and "Tor" rather than
        // countries; passing them on would be a lie the dashboard cannot detect.
        return LocationData(ip, if (code == "XX" || code == "T1") "" else code)
    }

    /**
     * Fills in a country the answering deployment could not name.
     *
     * Only a host that is itself told the caller's country can answer this.
     * Cloudflare and Vercel are; the Deno deployment is not, and returns the
     * field empty.
     *
     * Every candidate is one of ours, so the address is never handed to a
     * geolocation service merely to be labelled. They are tried in turn because
     * the first version of this asked Cloudflare alone — the host ranked last
     * inside Russia for being unreachable — so an address resolved through Deno
     * arrived with no country at all, which is exactly how this failed.
     *
     * Best effort on purpose, and on the same client, so the country describes
     * the same route the address was read from. An unanswered probe leaves the
     * country unknown rather than discarding an address the user asked for.
     */
    private fun ensureCountry(
        found: LocationData,
        client: OkHttpClient,
        source: IpEchoSources.Source,
        isRussianRegion: Boolean
    ): LocationData {
        if (found.countryCode.isNotBlank() || !source.isOwn) return found

        // Deliberately shorter than the client's own timeout: some of these
        // hosts are expected to be unreachable, and the address is already
        // resolved and waiting to be shown beside whatever country arrives.
        val probeClient = client.newBuilder()
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        for (probe in IpEchoSources.countryProbeSources(isRussianRegion)) {
            // The deployment that just answered has already said it knows no
            // country; asking it again only costs another round trip.
            if (probe.url == source.url) continue

            try {
                val request = Request.Builder().url(probe.url).build()
                val probed = probeClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    readLocation(response.body.string())
                }

                if (probed != null && probed.countryCode.isNotBlank()) {
                    return found.copy(countryCode = probed.countryCode)
                }
            } catch (e: Exception) {
                ProtonLogger.w("DashboardVM", "Country probe failed at ${probe.id}: ${e.message}")
            }
        }

        return found
    }

    private data class LocationData(val ip: String, val countryCode: String)

    override fun onCleared() {
        super.onCleared()
        // Shut down the shared OkHttpClient instances to release their thread pools and
        // connection pools when the ViewModel is destroyed, preventing resource leaks.
        // evictAll() closes sockets which involves network I/O, so it must run off the main thread.
        // viewModelScope is already cancelled at this point, so a standalone CoroutineScope is used
        // to ensure the cleanup coroutine actually executes on the IO dispatcher.
        CoroutineScope(Dispatchers.IO).launch {
            noProxyClient.dispatcher.executorService.shutdown()
            noProxyClient.connectionPool.evictAll()
            defaultClient.dispatcher.executorService.shutdown()
            defaultClient.connectionPool.evictAll()
        }
    }

    fun loadServers() {
        viewModelScope.launch {
            _errorMessage.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _errorMessage.value = context.getString(R.string.error_session_not_found)
                return@launch
            }

            try {
                vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier)
                    .onFailure { error ->
                        val cachedServers = vpnRepository.getCachedServers()
                        if (cachedServers.isEmpty()) {
                            _errorMessage.value = error.localizedMessage ?: context.getString(R.string.error_unknown)
                        }
                    }
            } finally {
            }
        }
    }

    fun disconnect() {
        ProtonLogger.action("Dashboard", "User clicked Disconnect")
        viewModelScope.launch {
            _vpnLocationText.value = null
            connectedServerState.setConnectedServer(null)
            amneziaVpnManager.disconnect()
        }
    }

    fun refreshCertificate() {
        amneziaVpnManager.checkAndRefreshCertificateProactively(force = true)
    }

    fun pauseVpn(durationMs: Long) {
        amneziaVpnManager.pauseVpn(durationMs)
    }

    fun resumeVpn() {
        viewModelScope.launch {
            vpnAutomationManager.resumeVpn()
        }
    }

    fun toggleConnection(server: LogicalServer) {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is DashboardUiState.Success) return@launch

            val isConnectedToAny = currentState.isConnected || currentState.isConnecting
            val isTargetServerConnected = currentState.connectedServer?.id == server.id

            if (isConnectedToAny) {
                if (isTargetServerConnected && currentState.isConnected) {
                    disconnect()
                } else {
                    initiateConnection(server)
                }
            } else {
                initiateConnection(server)
            }
        }
    }

    fun quickConnect() {
        ProtonLogger.action("Dashboard", "User clicked Quick Connect")
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is DashboardUiState.Success) return@launch

            when (currentState.quickConnectStrategy) {
                "recent" -> {
                    val lastServer = currentState.recentConnections.firstOrNull()
                    if (lastServer != null) {
                        initiateConnection(lastServer)
                    } else {
                        // Fallback to fastest if no recent
                        connectToFastest(currentState.servers)
                    }
                }
                "profile" -> {
                    val profile = currentState.profiles.find { it.id == currentState.quickConnectTargetId }
                    if (profile != null) {
                        connectWithProfile(profile, currentState.servers)
                    } else {
                        // Fallback to fastest if profile not found
                        connectToFastest(currentState.servers)
                    }
                }
                "server" -> {
                    val targetServer = currentState.servers.find { it.id == currentState.quickConnectTargetId }
                    if (targetServer != null) {
                        initiateConnection(targetServer)
                    } else {
                        connectToFastest(currentState.servers)
                    }
                }
                else -> {
                    // Default: "fastest"
                    connectToFastest(currentState.servers)
                }
            }
        }
    }

    private suspend fun connectToFastest(servers: List<LogicalServer>) {
        val bestServer = servers.minByOrNull { it.averageLoad }
        if (bestServer != null) {
            initiateConnection(bestServer)
        }
    }

    private suspend fun connectWithProfile(profile: VpnProfileEntity, allServers: List<LogicalServer>) {
        val session = sessionDao.getSession() ?: return

        val targetServer = findBestServerForProfile(profile, allServers) ?: return
        val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: targetServer.servers.minByOrNull { it.load } ?: return

        var obfuscationParams: AmneziaVpnManager.ObfuscationParams? = null
        if (profile.isObfuscationEnabled && profile.obfuscationProfileId != null) {
            val customProfiles = settingsManager.customProfiles.first()
            val standardProfileName = context.getString(R.string.obfuscation_config_standard)
            val selectedConfig = customProfiles.find { it.id == profile.obfuscationProfileId }
                ?: if (profile.obfuscationProfileId == "standard_1") ObfuscationProfile.getStandardProfile(standardProfileName) else null

            selectedConfig?.let {
                obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                    jc = it.jc, jmin = it.jmin, jmax = it.jmax,
                    s1 = it.s1, s2 = it.s2, s3 = it.s3, s4 = it.s4,
                    h1 = it.h1, h2 = it.h2, h3 = it.h3, h4 = it.h4,
                    i1 = it.i1, i2 = it.i2, i3 = it.i3, i4 = it.i4, i5 = it.i5
                )
            }
        }

        connectedServerState.setConnectedServer(targetServer)
        val vpnState = amneziaVpnManager.vpnState.value

        if (vpnState != AmneziaVpnManager.VpnState.DISCONNECTED) {
            amneziaVpnManager.reconnect(
                targetServer.id, physicalServer, session,
                overridePort = profile.port,
                overrideObfuscation = profile.isObfuscationEnabled,
                obfuscationParams = obfuscationParams,
                logicalServer = targetServer
            )
        } else {
            amneziaVpnManager.connect(
                targetServer.id, physicalServer, session,
                overridePort = profile.port,
                overrideObfuscation = profile.isObfuscationEnabled,
                obfuscationParams = obfuscationParams,
                logicalServer = targetServer
            )
        }

        if (!profile.autoOpenUrl.isNullOrEmpty()) {
            amneziaVpnManager.awaitTunnelAndOpenUrl(profile.autoOpenUrl)
        }
    }

    private fun findBestServerForProfile(profile: VpnProfileEntity, allServers: List<LogicalServer>): LogicalServer? {
        return vpnRepository.findBestServerForProfile(profile, allServers)
    }

    /**
     * Persistent traffic statistics for the dashboard stats card.
     * Kept separate from [uiState] to avoid growing the main combine().
     */
    val statsUiState: StateFlow<TrafficStatsUiState> = combine(
        trafficStatsDao.observeAll(),
        settingsManager.trafficStatsEnabled,
    ) { rows, enabled ->
        buildTrafficStatsUiState(rows, enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrafficStatsUiState())

    fun toggleTrafficStats() {
        viewModelScope.launch {
            settingsManager.setTrafficStatsEnabled(!settingsManager.trafficStatsEnabled.first())
        }
    }

    private fun buildTrafficStatsUiState(
        rows: List<TrafficStatsEntity>,
        enabled: Boolean,
    ): TrafficStatsUiState {
        val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val monthFormat = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val todayKey = dayFormat.format(java.util.Date())
        val monthPrefix = todayKey.substring(0, 7)
        val yearPrefix = todayKey.substring(0, 4)

        fun summarize(predicate: (TrafficStatsEntity) -> Boolean): TrafficPeriodSummary {
            var rx = 0L
            var tx = 0L
            var usage = 0L
            for (row in rows) {
                if (predicate(row)) {
                    rx += row.rxBytes
                    tx += row.txBytes
                    usage += row.usageSeconds
                }
            }
            return TrafficPeriodSummary(rx, tx, usage)
        }

        // Daily chart: last 30 calendar days, empty days included so the
        // curve keeps a stable time axis (matches the desktop behaviour).
        val byDay = rows.associateBy { it.day }
        val dayCal = java.util.Calendar.getInstance()
        dayCal.add(java.util.Calendar.DAY_OF_YEAR, -29)
        val daily = (0 until 30).map {
            val key = dayFormat.format(dayCal.time)
            dayCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val row = byDay[key]
            TrafficChartPoint(key.substring(8), (row?.rxBytes ?: 0L) + (row?.txBytes ?: 0L))
        }

        // Monthly chart: last 12 months.
        val byMonth = rows.groupBy { it.day.substring(0, 7) }
        val monthCal = java.util.Calendar.getInstance()
        monthCal.add(java.util.Calendar.MONTH, -11)
        val monthly = (0 until 12).map {
            val key = monthFormat.format(monthCal.time)
            monthCal.add(java.util.Calendar.MONTH, 1)
            val total = byMonth[key]?.sumOf { row -> row.rxBytes + row.txBytes } ?: 0L
            TrafficChartPoint(key.substring(5), total)
        }

        // Yearly chart: every year we have data for.
        val yearly = rows
            .groupBy { it.day.substring(0, 4) }
            .toSortedMap()
            .map { (year, list) ->
                TrafficChartPoint(year, list.sumOf { row -> row.rxBytes + row.txBytes })
            }

        return TrafficStatsUiState(
            enabled = enabled,
            today = summarize { it.day == todayKey },
            month = summarize { it.day.startsWith(monthPrefix) },
            year = summarize { it.day.startsWith(yearPrefix) },
            dailyChart = daily.toImmutableList(),
            monthlyChart = monthly.toImmutableList(),
            yearlyChart = yearly.toImmutableList(),
        )
    }

    fun setQuickConnectStrategy(strategy: String, targetId: String? = null) {
        viewModelScope.launch {
            settingsManager.setQuickConnectStrategy(strategy, targetId)
        }
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            _errorMessage.value = context.getString(R.string.error_session_not_found)
            return
        }

        // Reliable server selection: Fallback to any server with min load if status == 1 is absent.
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load }

        if (physicalServer != null) {
            connectedServerState.setConnectedServer(server)
            val vpnState = amneziaVpnManager.vpnState.value
            if (vpnState != AmneziaVpnManager.VpnState.DISCONNECTED) {
                amneziaVpnManager.reconnect(server.id, physicalServer, session, logicalServer = server)
            } else {
                amneziaVpnManager.connect(server.id, physicalServer, session, logicalServer = server)
            }
        } else {
            _errorMessage.value = context.getString(R.string.label_server_unavailable)
        }
    }
}
