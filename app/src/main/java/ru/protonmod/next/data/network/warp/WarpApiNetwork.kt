/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.warp

import android.content.Context
import android.net.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class WarpApiNetwork(val handle: Long?, val vpn: Boolean, val addresses: String)

/** Observe the default route itself: Wi-Fi -> mobile changes must invalidate an
 * old handshake even when internet availability never went false. */
internal fun warpApiNetworks(context: Context) = callbackFlow {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    fun publish() {
        val network = connectivity.activeNetwork
        val caps = connectivity.getNetworkCapabilities(network)
        trySend(WarpApiNetwork(network?.networkHandle?.takeIf {
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }, caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            network?.let { connectivity.getLinkProperties(it)?.linkAddresses?.toString() }.orEmpty()))
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = publish()
    }
    connectivity.registerDefaultNetworkCallback(callback)
    publish()
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
