/*
 * Copyright (c) 2021 Proton AG
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
package com.protonvpn.android.models.vpn

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.utils.Constants.VPN_CLIENT_IP
import com.protonvpn.android.utils.Constants.VPN_CLIENT_IP_V6
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP_V6
import com.protonvpn.android.vpn.CertificateRepository
import me.proton.core.network.domain.session.SessionId
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer

class ConnectionParamsWireguard(
    connectIntent: AnyConnectIntent,
    server: Server,
    port: Int,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol,
    ipv6SettingEnabled: Boolean,
) : ConnectionParams(
    connectIntent,
    server,
    connectingDomain,
    VpnProtocol.WireGuard,
    entryIp,
    port,
    transmission,
    ipv6SettingEnabled = ipv6SettingEnabled,
), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
    ): Config =
        getTunnelConfig(
            context.packageName,
            userSettings,
            sessionId,
            certificateRepository,
            computeAllowedIPs
        )

    @Throws(IllegalStateException::class)
    @VisibleForTesting
    suspend fun getTunnelConfig(
        myPackageName: String,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
    ): Config {
        val entryIp = entryIp ?: requireNotNull(connectingDomain?.getEntryIp(protocolSelection))

        val allowedIpsString =
            if (connectIntent is AnyConnectIntent.GuestHole) {
                "0.0.0.0/0, ::/0"
            } else {
                computeAllowedIPs(userSettings).joinToString(", ") { it.toCanonicalString() }
            }

        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(connectingDomain?.publicKeyX25519))
            .parseEndpoint("$entryIp:$port")
            .parseAllowedIPs(allowedIpsString)
            // Having persistent keepalive ensures that WG handshake will be triggered even if no data goes through
            // the tunnel - which fixes the issue on some devices where handshake is not triggered and we're stuck in
            // connecting state.
            .setPersistentKeepalive(60)
            .build()

        val (addresses, dns) = if (enableIPv6 == true && server.isIPv6Supported) {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4+6 tunnel")
            "$VPN_CLIENT_IP/32, $VPN_CLIENT_IP_V6/128" to "$VPN_SERVER_IP, $VPN_SERVER_IP_V6"
        } else {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4 tunnel")
            "$VPN_CLIENT_IP/32" to VPN_SERVER_IP
        }
        val splitTunneling = userSettings.splitTunneling
        val dnsServers: String = buildList {
            addAll(userSettings.customDns.effectiveDnsList)
            add(dns)
        }.joinToString(",")
        val iface = Interface.Builder()
            .parseAddresses(addresses)
            .parseDnsServers(dnsServers.trim())
            .parsePrivateKey(certificateRepository.getX25519Key(sessionId))
            // AmneziaWG Obfuscation Parameters
            // Maps to Jc
            .setJunkPacketCount(3)
            // Maps to Jmin
            .setJunkPacketMinSize(1)
            // Maps to Jmax
            .setJunkPacketMaxSize(3)
            // Maps to S1
            .setInitPacketJunkSize(0)
            // Maps to S2
            .setResponsePacketJunkSize(0)
            // Maps to H1 (InitPacketMagicHeader)
            .setInitPacketMagicHeader("1")
            // Maps to H2 (ResponsePacketMagicHeader)
            .setResponsePacketMagicHeader("2")
            // Maps to H3 (UnderloadPacketMagicHeader)
            .setUnderloadPacketMagicHeader("3")
            // Maps to H4 (TransportPacketMagicHeader)
            .setTransportPacketMagicHeader("4")
            // Special Junk I1 packet (AmneziaWG)
            .setSpecialJunkI1("<b 0xc6000000010843290a47ba8ba2ed000044d0e3efd9326adb60561baa3bc4b52471b2d459ddcc9a508dffddc97e4d40d811d3de7bc98cf06ea85902361ca3ae66b2a99c7de96f0ace4ba4710658aefde6dec6837bc1a48f47bbd63c6e60ff494d3e1bea5f13927922401c40b0f4570d26be6806b506a9ff5f75ca86fae5f8175d4b6bfd418df9b922cdff8e60b06decfe66f2b07da61a47b5c8b32fa999d8feac21c8878b6e15ee03b8388b2afd9ffd3b46753b0284907b10747e526eebf287ff08735929c4c5e4784a5e2ad3dd8ac8200d0e99ad1219e54060ddc72813e8a3e2291ac713c5f3251c5d748fd68782a2e8eb0c021e437a79aafb253efae3ee72e1051b647c45b676d3b9e474d4f60c7bf7d328106cb94f67eaf2c991cd7043371debbf2b4159b8f80f5da0e1b18f4da35fca0a88026b375f1082731d1cbbe9ba3ae2bfefec250ee328ded7f8330d4cda38f74a7fe10b58ace936fc83cfcb3e1ebed520f7559549a8f20568a248e16949611057a3dd9952bae9b7be518c2b5b2568b8582c165c73a6e8f9b042ec9702704a94dd99893421310d43ffc9caf003ff5fc7bcd718f3fa99d663a8bbad6b595ec1d4cf3c0ed1668d0541c4e5b7e5ded40c6628eb64b29f514424d08d8522ddf7b856e9b820441907177a3dbd9b958172173be8c45c8c7b1816fe4d24927f9b12778153fc118194786c6cf49bc5cf09177f73be27917a239592f9acd9a21150abbd1ca93b1e305dc64d9883429a032c3179e0639592c248cbacec00c90bfb5d5eaf8920bf80c47085a490ead8d0af45f6754e8ae5692f86be02c480ca2a1e6156dccf1bcb5c911c05e3c3a946ca23461669c57d287dcfa9dd187fc6a58394f0b2878c07e1d8cb6be41725d49c118e9ddbe1ae6e5d1a04b36ad98a24f0378deea84febb60b22dc81d8377fb3f21069d83e90b9eba2b6b0ea95acf5fd0482a00d064a9b73e0b3732fde45404e22349a514151381fc6095a8204234359c28765e360a57eb222418b11be704651e4da1b52b135d31ba63a7f06a0f7b8b6177f9bd02fb517877a1340e59d8dbe52ea8135bc80b2aa1959539803a31720ac949c7bf0785c2e413e8b83dd4fd40d8a63fbd832ecb727d0c0df04ce10dac6a7d6d75e264aaf856e7485cc2c4e1749f169e5ad4de6f89a2333e362910dd0d962e3bf59a353df5760fd15956fe32e40f863ea020e9709aa9a9ebeffc885e64231dc6fc384bc6a9e7e5c64c0eaf39f9f14a13658883ee8dd94737cb3a8c2f7020bfacb80f0122866635873e248e22b5a5abc84d507e1720d3fb5f827d75c1d39235a361a217eb0587d1639b0b31aef1fe91958220fcf934c2517dea2f1afe51cd63ac31b5f9323a427c36a5442f8a89b7494f1592666f62be0d8cf67fdf5ef38fafc55b7b4f569a105dfa9925f0a41913c6ee13064d4b83f9ee1c3231c402d68a624e2388e357144be99197dcafb92118d9a9ec6fe832771e12448a146fb5b9620a4718070b368aab646b03cce41ec4d5d9a9c880a9cff06aba991cc0845030abbac87c67255f0373eb38444a51d0958e57c7a33042697465c84abe6791cb8f28e484c4cd04f10791ad911b0dcc217f66cb3aa5fcdbb1e2be88139c4ac2652e469122408feba59ad04f66eb8ab8c80aaf10c2ec1f80b5be111d3ccc832df2395a947e335e7908fda5dcdaa14a61f0fa7156c94b1c96e5c191d850e341adc2e22c8f69fcfa5c3e403eadc933f18be3734bc345def4f40ea3e12>")
            .setMtu(1420)
            .splitTunnelingApps(connectIntent, myPackageName, splitTunneling, userSettings.lanConnectionsAllowDirect)
            .build()

        return Config.Builder().addPeer(peer).setInterface(iface).build()
    }

    private class SplitTunnelAppsWgConfigurator(private val builder: Interface.Builder) : SplitTunnelAppsConfigurator {
        override fun includeApplications(packageNames: List<String>) {
            builder.includeApplications(packageNames)
        }

        override fun excludeApplications(packageNames: List<String>) {
            builder.excludeApplications(packageNames)
        }
    }

    private fun Interface.Builder.splitTunnelingApps(
        connectIntent: AnyConnectIntent,
        myPackageName: String,
        splitTunneling: SplitTunnelingSettings,
        allowDirectLanConnections: Boolean
    ): Interface.Builder {
        val configurator = SplitTunnelAppsWgConfigurator(this)
        applyAppsSplitTunneling(configurator, connectIntent, myPackageName, splitTunneling, allowDirectLanConnections)
        return this
    }
}