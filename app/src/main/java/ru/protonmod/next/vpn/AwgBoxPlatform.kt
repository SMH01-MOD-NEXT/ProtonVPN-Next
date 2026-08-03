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

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import ru.protonmod.next.utils.ProtonLogger
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface

private const val TAG = "AwgBoxPlatform"

/** Failure message used when the OS no longer grants this app the VPN consent. */
internal const val VPN_PERMISSION_REVOKED = "VPN permission was revoked"

/** Android platform bridge required by libbox. */
internal data class SplitTunnelingAppPolicy(
    val allowedApps: Set<String> = emptySet(),
    val disallowedApps: Set<String> = emptySet()
)

internal fun splitTunnelingAppPolicy(
    enabled: Boolean,
    mode: String,
    selectedApps: Set<String>,
    vpnPackageName: String
): SplitTunnelingAppPolicy = when {
    !enabled -> SplitTunnelingAppPolicy()
    mode == "include" -> SplitTunnelingAppPolicy(
        allowedApps = selectedApps + vpnPackageName
    )
    else -> SplitTunnelingAppPolicy(
        disallowedApps = selectedApps
    )
}

class AwgBoxPlatform(
    private val service: VpnService,
    private val vpnNetworkMonitor: VpnNetworkMonitor,
    private val onTunOpened: (ParcelFileDescriptor) -> Unit
) : PlatformInterface {
    private val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var splitTunnelingEnabled = false
    @Volatile private var splitTunnelingMode = "exclude"
    @Volatile private var splitTunnelingApps: Set<String> = emptySet()

    fun configureSplitTunneling(enabled: Boolean, mode: String, selectedApps: Set<String>) {
        splitTunnelingEnabled = enabled
        splitTunnelingMode = mode
        splitTunnelingApps = selectedApps.toSet()
    }

    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) { service.protect(fd) }
    override fun useProcFS() = false
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun clearDNSCache() = Unit
    override fun readWIFIState(): WIFIState? = null
    override fun sendNotification(notification: Notification) = Unit

    override fun openTun(options: TunOptions): Int {
        // VpnService.prepare() throws SecurityException ("<package> does not belong to uid ...")
        // when the consent record belongs to another Android user/profile or was invalidated by
        // a reinstall. Translate it into the ordinary "permission revoked" failure so the
        // connection fails cleanly and the user is asked to grant VPN access again (ANDROID-21R).
        val vpnConsentMissing = try {
            VpnService.prepare(service) != null
        } catch (error: SecurityException) {
            ProtonLogger.w(TAG, "VPN consent no longer owned by this app: ${error.message}")
            true
        }
        check(!vpnConsentMissing) { VPN_PERMISSION_REVOKED }
        val builder = service.Builder()
            .setSession(service.getString(ru.protonmod.next.R.string.vpn_session_name))
            .setMtu(options.mtu)
            .setMetered(false)

        options.inet4Address.forEachRemaining { builder.addAddress(it.address(), it.prefix()) }
        options.inet6Address.forEachRemaining { builder.addAddress(it.address(), it.prefix()) }
        // libbox 1.14 hands over the full DNS server list instead of a single StringBox.
        options.dnsServerAddress.forEachRemaining { address ->
            if (address.isNotBlank()) builder.addDnsServer(address)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.inet4RouteAddress.forEachRemaining { builder.addRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet6RouteAddress.forEachRemaining { builder.addRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet4RouteExcludeAddress.forEachRemaining { builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet6RouteExcludeAddress.forEachRemaining { builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
        } else {
            options.inet4RouteRange.forEachRemaining { builder.addRoute(it.address(), it.prefix()) }
            options.inet6RouteRange.forEachRemaining { builder.addRoute(it.address(), it.prefix()) }
        }

        // App split tunneling is applied from the explicit connection policy. Relying on
        // TunOptions package iterators made a previous Include filter leak into Exclude mode
        // during engine reloads, effectively bypassing the VPN for every application.
        val appPolicy = splitTunnelingAppPolicy(
            enabled = splitTunnelingEnabled,
            mode = splitTunnelingMode,
            selectedApps = splitTunnelingApps,
            vpnPackageName = service.packageName
        )
        appPolicy.allowedApps.sorted().forEach { packageName ->
            try { builder.addAllowedApplication(packageName) } catch (_: PackageManager.NameNotFoundException) { }
        }
        appPolicy.disallowedApps.sorted().forEach { packageName ->
            try { builder.addDisallowedApplication(packageName) } catch (_: PackageManager.NameNotFoundException) { }
        }

        val descriptor = builder.establish() ?: error("Failed to establish Android TUN")
        onTunOpened(descriptor)
        return descriptor.fd
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        check(uid != Process.INVALID_UID) { "Connection owner not found" }
        return ConnectionOwner().apply {
            userId = uid
            val packages = service.packageManager.getPackagesForUid(uid).orEmpty().toList()
            userName = packages.firstOrNull().orEmpty()
            setAndroidPackageNames(BoxStringIterator(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        closeDefaultInterfaceMonitor(listener)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishDefaultNetwork(listener, network)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publishDefaultNetwork(listener, network)
            override fun onLost(network: Network) {
                if (connectivity.activeNetwork == null) listener.updateDefaultInterface("", -1, false, false)
            }
        }
        networkCallback = callback
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback
        )
        connectivity.activeNetwork?.let { publishDefaultNetwork(listener, it) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun publishDefaultNetwork(listener: InterfaceUpdateListener, network: Network) {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return
        // The underlying physical network must be published; selecting our own VPN TUN
        // would route the AWG endpoint back into itself.
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        val properties = connectivity.getLinkProperties(network) ?: return
        val name = properties.interfaceName ?: return
        val index = runCatching { NetworkInterface.getByName(name)?.index ?: -1 }.getOrDefault(-1)
        listener.updateDefaultInterface(
            name,
            index,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            false
        )
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<BoxNetworkInterface>()
        for (tracked in vpnNetworkMonitor.getTrackedNetworks()) {
            val properties = tracked.linkProperties ?: continue
            val capabilities = tracked.capabilities ?: continue
            val name = properties.interfaceName ?: continue
            val javaInterface = runCatching { NetworkInterface.getByName(name) }.getOrNull() ?: continue
            result += BoxNetworkInterface().apply {
                this.name = name
                index = javaInterface.index
                mtu = runCatching { javaInterface.mtu }.getOrDefault(1500)
                addresses = BoxStringIterator(javaInterface.interfaceAddresses.map { address ->
                    val host = if (address.address is Inet6Address) {
                        Inet6Address.getByAddress(address.address.address).hostAddress
                    } else address.address.hostAddress
                    "$host/${address.networkPrefixLength}"
                })
                dnsServer = BoxStringIterator(properties.dnsServers.mapNotNull { it.hostAddress })
                type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        return BoxNetworkIterator(result)
    }

    /**
     * libbox announces the name it gave our TUN so the platform can keep it out of its own
     * routing decisions. [publishDefaultNetwork] already rejects every VPN transport, so the
     * name carries no extra information here.
     */
    override fun registerMyInterface(name: String) = Unit

    // Neighbour discovery needs the kernel ARP/NDP table, which is unreachable from an
    // unprivileged Android app. Reporting failure keeps libbox on its own resolver.
    override fun startNeighborMonitor(listener: NeighborUpdateListener): Unit =
        throw UnsupportedOperationException("Neighbor table is unavailable on Android")

    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit

    // Tailscale, its SSH server and the platform bridge are all excluded from this AAR;
    // libbox only reaches the members below when a feature we do not build asks for them.
    override fun usePlatformShell() = false
    override fun checkPlatformShell() = Unit
    override fun tailscaleHostname() = ""
    override fun usePlatformBridge() = false

    override fun openShellSession(
        user: PlatformUser,
        command: String,
        environ: StringIterator,
        term: String,
        rows: Int,
        cols: Int
    ): ShellSession = throw UnsupportedOperationException("Platform shell is not supported")

    override fun lookupUser(username: String): PlatformUser =
        throw UnsupportedOperationException("Platform users are not supported")

    override fun lookupSFTPServer(): String =
        throw UnsupportedOperationException("SFTP server is not supported")

    override fun readSystemSSHHostKey(): String =
        throw UnsupportedOperationException("System SSH host key is not available")

    override fun createBridge(options: BridgeOptions): BridgeSession =
        throw UnsupportedOperationException("Platform bridge is not supported")
}

internal class BoxStringIterator(values: Collection<String>) : StringIterator {
    private val iterator = values.iterator()
    override fun hasNext() = iterator.hasNext()
    override fun next() = iterator.next()
    override fun len() = 0
}

private class BoxNetworkIterator(values: Collection<BoxNetworkInterface>) : NetworkInterfaceIterator {
    private val iterator = values.iterator()
    override fun hasNext() = iterator.hasNext()
    override fun next() = iterator.next()
}

private inline fun io.nekohasekai.libbox.RoutePrefixIterator.forEachRemaining(block: (io.nekohasekai.libbox.RoutePrefix) -> Unit) {
    while (hasNext()) block(next())
}

private inline fun StringIterator.forEachRemaining(block: (String) -> Unit) {
    while (hasNext()) block(next())
}
