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

package ru.protonmod.next.vpn

import kotlinx.coroutines.flow.first
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import javax.inject.Inject

/** Quick-connect work may outlive the QS service; never retain a Service or Activity here. */
class VpnQuickConnect @Inject constructor(
    private val amneziaVpnManager: AmneziaVpnManager,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager,
    private val profileDao: ProfileDao
) {
    suspend fun connect() {
        val session = sessionDao.getSession() ?: return
        val strategy = settingsManager.quickConnectStrategy.first()
        val targetId = settingsManager.quickConnectTargetId.first()

        val servers = vpnRepository.getCachedServers()
        if (servers.isEmpty()) return

        when (strategy) {
            "recent" -> {
                // Connect to the very first available server
                val bestServer = servers.minByOrNull { it.averageLoad }
                if (bestServer != null) initiateConnection(bestServer)
            }
            "server" -> {
                val target = servers.find { it.id == targetId }
                if (target != null) {
                    initiateConnection(target)
                } else {
                    val bestServer = servers.minByOrNull { it.averageLoad }
                    if (bestServer != null) initiateConnection(bestServer)
                }
            }
            "profile" -> {
                val profile = profileDao.getAllProfilesFlow().first().find { it.id == targetId }
                if (profile != null) {
                    val targetServer = findBestServerForProfile(profile, servers)
                    if (targetServer != null) {
                        val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
                            ?: targetServer.servers.minByOrNull { it.load }

                        if (physicalServer != null) {
                            var obfuscationParams: AmneziaVpnManager.ObfuscationParams? = null
                            if (profile.isObfuscationEnabled && profile.obfuscationProfileId != null) {
                                val customProfiles = settingsManager.customProfiles.first()
                                val selectedConfig = customProfiles.find { it.id == profile.obfuscationProfileId }
                                    ?: if (profile.obfuscationProfileId == "standard_1") {
                                        ObfuscationProfile.getStandardProfile()
                                    } else null

                                selectedConfig?.let {
                                    obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                                        jc = it.jc, jmin = it.jmin, jmax = it.jmax,
                                        s1 = it.s1, s2 = it.s2, s3 = it.s3, s4 = it.s4,
                                        h1 = it.h1, h2 = it.h2, h3 = it.h3, h4 = it.h4,
                                        i1 = it.i1, i2 = it.i2, i3 = it.i3, i4 = it.i4, i5 = it.i5
                                    )
                                }
                            }

                            amneziaVpnManager.connect(
                                targetServer.id, physicalServer, session,
                                overridePort = profile.port,
                                overrideObfuscation = profile.isObfuscationEnabled,
                                obfuscationParams = obfuscationParams,
                                logicalServer = targetServer
                            )
                        }
                    }
                } else {
                    val bestServer = servers.minByOrNull { it.averageLoad }
                    if (bestServer != null) initiateConnection(bestServer)
                }
            }
            else -> {
                val bestServer = servers.minByOrNull { it.averageLoad }
                if (bestServer != null) initiateConnection(bestServer)
            }
        }
    }

    private fun findBestServerForProfile(profile: VpnProfileEntity, allServers: List<LogicalServer>): LogicalServer? {
        return vpnRepository.findBestServerForProfile(profile, allServers)
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession() ?: return
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load } ?: return

        amneziaVpnManager.connect(server.id, physicalServer, session, logicalServer = server)
    }

}
