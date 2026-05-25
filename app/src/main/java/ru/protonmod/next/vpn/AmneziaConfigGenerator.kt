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

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

interface AmneziaConfigGenerator {
    fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean = false,
        selectedApps: Set<String> = emptySet(),
        selectedIps: Set<String> = emptySet(),
        port: Int = 1194,
        certificate: String? = null,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String
}

@Singleton
class AmneziaConfigGeneratorImpl @Inject constructor(
    private val nextConfigGenerator: Lazy<NextConfigGenerator>,
    private val ipSubnetCalculator: IpSubnetCalculator
) : AmneziaConfigGenerator {
    override fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean,
        selectedApps: Set<String>,
        selectedIps: Set<String>,
        port: Int,
        certificate: String?,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String {
        val allowedIpsList = when {
            isIncludeMode -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else selectedIps.toList()
            else -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else ipSubnetCalculator.complementOfExcluded(selectedIps)
        }

        return nextConfigGenerator.get().buildConfig(
            serverPublicKey = serverPublicKey,
            privateKey = privateKey,
            localIp = localIp,
            dnsServer = dnsServer,
            targetIp = targetIp,
            isIncludeMode = isIncludeMode,
            selectedApps = selectedApps,
            selectedIps = allowedIpsList.toSet(),
            port = port,
            certificate = certificate,
            obfuscationParams = obfuscationParams
        )
    }
}
