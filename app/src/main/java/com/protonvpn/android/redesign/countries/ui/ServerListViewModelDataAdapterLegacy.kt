/*
 * Copyright (c) 2024 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.countries.ui

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_TOR
import com.protonvpn.android.servers.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.countries.TranslationsData
import com.protonvpn.android.redesign.countries.city
import com.protonvpn.android.redesign.countries.state
import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.isVirtualLocation
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.hasFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.EnumSet
import javax.inject.Inject
import kotlin.math.roundToInt

class ServerListViewModelDataAdapterLegacy @Inject constructor(
    private val serverManager2: ServerManager2,
    private val currentUser: CurrentUser,
) : ServerListViewModelDataAdapter {

    private val userTierFlow = currentUser.vpnUserFlow.map {
        // set to true to test for plus user
        val forcePlus = false
        if (forcePlus) {
            VpnUser.PLUS_TIER
        } else {
            it?.userTier ?: VpnUser.FREE_TIER
        }
    }

    override suspend fun countriesCount(): Int = serverManager2.getCountriesCount()

    override suspend fun availableTypesFor(country: CountryId?): Set<ServerFilterType> {
        val servers = serverManager2.allServersFlow.first()
        val availableTypes = initAvailableTypes()
        val userTier = userTierFlow.first()
        for (server in servers.asFilteredSequence(userTier)) {
            if (country == null || server.exitCountry == country.countryCode)
                availableTypes.update(server)
        }
        return availableTypes
    }

    override fun countries(
        filter: ServerFilterType
    ): Flow<List<ServerGroupItemData.Country>> =
        combine(serverManager2.allServersFlow, userTierFlow) { servers, userTier ->
            val secureCore = filter == ServerFilterType.SecureCore
            val entryCountryId = if (secureCore)
                CountryId.fastest
            else
                null
            val countries = servers
                .asFilteredSequence(userTier, filter)
                .map { it.exitCountry }
                .distinct()
                .toList()
            countries.mapNotNull { countryCode ->
                serverManager2
                    .getVpnExitCountry(countryCode, secureCore)
                    ?.serverList
                    ?.takeIf { it.isNotEmpty() }
                    ?.asFilteredSequence(userTier, filter)
                    ?.toList()
                    ?.toCountryItem(countryCode, entryCountryId)
            }
        }

    override fun cities(
        filter: ServerFilterType,
        country: CountryId,
        translations: TranslationsData?,
    ): Flow<List<ServerGroupItemData.City>> =
        combine(serverManager2.allServersFlow, userTierFlow) { servers, userTier ->
            val filteredServers = servers.asFilteredSequence(userTier, filter, country)
            val hasStates = filteredServers.any { it.state != null }
            val groupBySelector = if (hasStates) Server::state else Server::city
            val availableTypes = initAvailableTypes()
            filteredServers
                .groupBy(groupBySelector)
                .mapNotNull { (cityOrState, servers) ->
                    availableTypes.update(servers)
                    toCityItem(translations, hasStates, cityOrState, servers)
                }
        }

    override fun servers(
        filter: ServerFilterType,
        country: CountryId?,
        cityStateId: CityStateId?,
        gatewayName: String?,
    ): Flow<List<ServerGroupItemData.Server>> =
        combine(serverManager2.allServersFlow, userTierFlow) { servers, userTier ->
            val availableTypes = initAvailableTypes()
            servers
                .asFilteredSequence(userTier, filter, country, cityStateId, gatewayName)
                .onEach { availableTypes.update(it) }
                .map(Server::toServerItem)
                .toList()
        }

    override fun entryCountries(country: CountryId): Flow<List<ServerGroupItemData.Country>> =
        combine(serverManager2.allServersFlow, userTierFlow) { servers, userTier ->
            val exitCountryServers = serverManager2.getVpnExitCountry(country.countryCode, true)?.serverList
            if (exitCountryServers == null)
                emptyList()
            else {
                val entryCountries = exitCountryServers.groupBy { it.entryCountry }
                entryCountries.map { (entryCode, servers) ->
                    servers.toCountryItem(country.countryCode, CountryId(entryCode))
                }
            }
        }

    override suspend fun haveStates(country: CountryId): Boolean {
        val userTier = userTierFlow.first()
        return serverManager2.allServersFlow.first()
            .asFilteredSequence(userTier, country = country)
            .any { it.state != null }
    }

    override fun gateways(): Flow<List<ServerGroupItemData.Gateway>> =
        combine(serverManager2.allServersFlow, userTierFlow) { servers, userTier ->
            val gateways = servers
                .asFilteredSequence(userTier, forceIncludeGateways = true)
                .groupBy { it.gatewayName }
            gateways.mapNotNull { (gatewayName, servers) ->
                gatewayName?.let { servers.toGatewayItem(gatewayName) }
            }
        }

    override suspend fun getHostCountry(countryId: CountryId): CountryId? {
        val hostCountry = serverManager2.getVpnExitCountry(
            countryId.countryCode,
            secureCoreCountry = false
        )?.serverList?.firstOrNull {
            it.isVirtualLocation
        }?.hostCountry
        return hostCountry?.let { CountryId(it) }
    }

    private fun List<Server>.asFilteredSequence(
        userTier: Int,
        filter: ServerFilterType = ServerFilterType.All,
        country: CountryId? = null,
        cityStateId: CityStateId? = null,
        gatewayName: String? = null,
        forceIncludeGateways: Boolean = false,
    ): Sequence<Server> {
        return asSequence().filter { server ->
            (server.tier <= userTier) &&
                    filter.isMatching(server) &&
                    (country == null || country.countryCode == server.exitCountry) &&
                    (cityStateId == null || cityStateId.matches(server)) &&
                    ((forceIncludeGateways && gatewayName == null) || gatewayName == server.gatewayName)
        }
    }
}

fun List<Server>.toCountryItem(countryCode: String, entryCountryId: CountryId?, match: TextMatch? = null) = ServerGroupItemData.Country(
    countryId = CountryId(countryCode),
    entryCountryId = entryCountryId,
    inMaintenance = all { !it.online },
    tier = minOf { it.tier },
    textMatch = match,
)

fun Server.toServerItem(match: TextMatch? = null) = ServerGroupItemData.Server(
    countryId = CountryId(exitCountry),
    serverId = ServerId(serverId),
    name = serverName,
    loadPercent = load.roundToInt(),
    serverFeatures = serverFeatures,
    isVirtualLocation = isVirtualLocation,
    inMaintenance = !online,
    tier = tier,
    entryCountryId = if (isSecureCoreServer) CountryId(entryCountry) else null,
    gatewayName = gatewayName,
    textMatch = match,
)

fun toCityItem(
    translations: TranslationsData?,
    isState: Boolean,
    cityOrState: String?,
    servers: List<Server>,
    match: TextMatch? = null
) : ServerGroupItemData.City? {
    if (cityOrState == null || servers.isEmpty())
        return null

    val server = servers.first()
    val country = CountryId(server.exitCountry)

    //TODO: what to do with servers without a state if hasStates is true
    return ServerGroupItemData.City(
        countryId = country,
        cityStateId = CityStateId(cityOrState, isState),
        name = if (isState) {
            translations.state(country, cityOrState)
        } else {
            translations.city(country, cityOrState)
        },
        inMaintenance = servers.all { !it.online },
        tier = servers.minOf { it.tier },
        textMatch = match,
    )
}

fun List<Server>.toGatewayItem(gatewayName: String, match: TextMatch? = null) =
    ServerGroupItemData.Gateway(
        gatewayName = gatewayName,
        inMaintenance = all { !it.online },
        tier = minOf { it.tier },
        textMatch = match
    )

private val Server.serverFeatures get() = buildSet {
    if (features.hasFlag(SERVER_FEATURE_P2P))
        add(ServerFeature.P2P)
    if (features.hasFlag(SERVER_FEATURE_TOR))
        add(ServerFeature.Tor)
}

fun CityStateId.matches(server: Server) =
    name == if (isState) server.state else server.city

fun ServerFilterType.isMatching(server: Server) = when (this) {
    ServerFilterType.All -> !server.isSecureCoreServer
    ServerFilterType.SecureCore -> server.isSecureCoreServer
    ServerFilterType.Tor -> server.isTor
    ServerFilterType.P2P -> server.isP2pServer
}

private fun initAvailableTypes() = EnumSet.of(ServerFilterType.All)

private fun EnumSet<ServerFilterType>.update(server: Server) {
    if (server.isSecureCoreServer) add(ServerFilterType.SecureCore)
    if (server.isTor) add(ServerFilterType.Tor)
    if (server.isP2pServer) add(ServerFilterType.P2P)
}

private fun EnumSet<ServerFilterType>.update(servers: List<Server>) = servers.forEach { update(it) }