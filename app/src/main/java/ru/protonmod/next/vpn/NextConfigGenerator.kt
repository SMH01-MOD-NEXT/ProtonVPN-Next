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

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextConfigGenerator @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("next")
        }
    }

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
    ): String {
        return generateConfigNative(
            serverPublicKey,
            privateKey,
            localIp,
            dnsServer,
            targetIp,
            isIncludeMode,
            selectedApps.toTypedArray(),
            selectedIps.toTypedArray(),
            port,
            certificate ?: "",
            obfuscationParams
        )
    }

    private external fun generateConfigNative(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean,
        selectedApps: Array<String>,
        selectedIps: Array<String>,
        port: Int,
        certificate: String,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String
}
