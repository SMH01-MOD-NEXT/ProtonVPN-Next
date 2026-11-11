/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.vpn

import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.serialization.json.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import ru.protonmod.next.data.network.WarpEndpoints
import java.io.File
import java.lang.reflect.Proxy

/** Starts the real native service, not only checkConfig. No VpnService/TUN,
 * routes, real accounts or public endpoints. The physical peer is loopback. */
@RunWith(AndroidJUnit4::class)
class WarpChainStartupInstrumentedTest {
    @Test fun ipv4OnlyWireGuardAndAllAwgWarpProfilesStartAsProtonDetours() {
        val generator = AwgBoxConfigGeneratorImpl(IpSubnetCalculatorImpl())
        val raw = """
            [Interface]
            PrivateKey = AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=
            Address = 172.16.0.2/32
            DNS = 1.1.1.1
            MTU = 1280
            [Peer]
            PublicKey = BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=
            Endpoint = 127.0.0.1:9
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 0
            Reserved = 1, 2, 3
        """.trimIndent()
        (0..3).forEach { profile ->
            val hop = ImportedWireGuard.encode(WarpEndpoints.apply(raw, profile = profile))
            val generated = generator.buildConfig(
                serverPublicKey = "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                privateKey = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
                localIp = "10.2.0.2", dnsServer = "10.2.0.1", targetIp = "192.0.2.10",
                proxyChainConfig = hop,
                obfuscationParams = AmneziaVpnManager.ObfuscationParams(0, 0, 0, 0, 0, h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""),
            )
            val config = Json.parseToJsonElement(generated).jsonObject
            // Keep the actual generated endpoints/detours; remove only OS VPN plumbing.
            val route = config.getValue("route").jsonObject + ("auto_detect_interface" to JsonPrimitive(false))
            val startup = JsonObject(config + mapOf("inbounds" to JsonArray(emptyList()),
                "route" to JsonObject(route), "log" to buildJsonObject { put("disabled", true) })).toString()
            val server = CommandServer(handler(), platform())
            try {
                server.start()
                server.startOrReloadService(startup, OverrideOptions())
                server.closeService()
            } finally { server.close() }
        }
    }

    @Test fun apiProxyDoesNotMistakeListeningForHandshake() {
        val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        val raw = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = $key\nEndpoint = 127.0.0.1:9\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 0\nReserved = 1, 2, 3\n"
        (0..3).forEach { profile ->
            val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val link = ImportedWireGuard.encode(WarpEndpoints.apply(raw, profile = profile))
            val config = ru.protonmod.next.data.network.warp.WarpApiConfig.build(link, port, "fixture", "fixture-secret")
            val proxy = Libbox.newApiWarpProxy(config, platform())
            try {
                org.junit.Assert.assertFalse(proxy.handshakeConfirmed())
                java.net.Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 1000
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                    org.junit.Assert.assertEquals(5, socket.getInputStream().read())
                    org.junit.Assert.assertEquals(255, socket.getInputStream().read()) // auth required
                }
                org.junit.Assert.assertFalse(proxy.handshakeConfirmed())
            } finally { proxy.close() }
            org.junit.Assert.assertFalse(proxy.handshakeConfirmed())
        }
    }

    @Test fun apiProxyReportsRealLoopbackCryptographicHandshakesForAllProfiles() {
        val serverKey = org.bouncycastle.crypto.params.X25519PrivateKeyParameters(ByteArray(32) { 2 }, 0)
        val clientKey = org.bouncycastle.crypto.params.X25519PrivateKeyParameters(ByteArray(32) { 1 }, 0)
        fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)
        fun raw(private: ByteArray, public: ByteArray, address: String, port: Int, keepalive: Int) =
            "[Interface]\nPrivateKey = ${b64(private)}\nAddress = $address/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = ${b64(public)}\nEndpoint = 127.0.0.1:$port\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = $keepalive\n"
        fun fixture(json: String, serverPort: Int? = null): String {
            val config = Json.parseToJsonElement(json).jsonObject
            val route = JsonObject(config.getValue("route").jsonObject + ("auto_detect_interface" to JsonPrimitive(false)))
            if (serverPort == null) return JsonObject(config + ("route" to route)).toString()
            val endpoint = config.getValue("endpoints").jsonArray.single().jsonObject
            val peer = endpoint.getValue("peers").jsonArray.single().jsonObject
            val passive = JsonObject(endpoint + mapOf("listen_port" to JsonPrimitive(serverPort),
                "peers" to JsonArray(listOf(JsonObject(peer - "address" - "port")))))
            return JsonObject(config + mapOf("route" to route, "inbounds" to JsonArray(emptyList()),
                "endpoints" to JsonArray(listOf(passive)))).toString()
        }
        fun config(text: String, port: Int) = ru.protonmod.next.data.network.warp.WarpApiConfig.build(
            ImportedWireGuard.encode(text), port, "fixture", "fixture-secret")
        (0..3).forEach { profile ->
            val udp = java.net.DatagramSocket(0).use { it.localPort }
            val tcp = java.net.ServerSocket(0).use { it.localPort }
            val serverRaw = raw(serverKey.encoded, clientKey.generatePublicKey().encoded, "172.16.0.1", 9, 0)
            val server = Libbox.newApiWarpProxy(fixture(config(serverRaw, tcp), udp), platform())
            try {
                val clientRaw = WarpEndpoints.apply(raw(clientKey.encoded, serverKey.generatePublicKey().encoded,
                    "172.16.0.2", udp, 1), profile = profile)
                val client = Libbox.newApiWarpProxy(fixture(config(clientRaw, tcp)), platform())
                try {
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
                    while (!client.handshakeConfirmed() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
                    org.junit.Assert.assertTrue("Actual handshake not observed for profile $profile", client.handshakeConfirmed())
                } finally { client.close() }
            } finally { server.close() }
        }
    }

    @Test fun apiProxyUsesProductionPhysicalBridgeWithoutCreatingVpn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.junit.Assume.assumeTrue(connectivity.allNetworks.any { n -> connectivity.getNetworkCapabilities(n)?.let {
            it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        } == true })
        val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        val raw = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\n[Peer]\nPublicKey = $key\nEndpoint = 127.0.0.1:9\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 0\n"
        val port = java.net.ServerSocket(0).use { it.localPort }
        val config = ru.protonmod.next.data.network.warp.WarpApiConfig.build(ImportedWireGuard.encode(raw), port, "fixture", "secret")
        val proxy = Libbox.newApiWarpProxy(config, AwgBoxPlatform(context))
        try { org.junit.Assert.assertFalse(proxy.handshakeConfirmed()) } finally { proxy.close() }
    }

    private fun handler(): CommandServerHandler = Proxy.newProxyInstance(
        CommandServerHandler::class.java.classLoader, arrayOf(CommandServerHandler::class.java)) { _, method, _ ->
        when (method.name) {
            "getSystemProxyStatus" -> SystemProxyStatus()
            "connectSSHAgent" -> throw UnsupportedOperationException("No SSH in native startup test")
            else -> null
        }
    } as CommandServerHandler

    private fun platform(): PlatformInterface = Proxy.newProxyInstance(
        PlatformInterface::class.java.classLoader, arrayOf(PlatformInterface::class.java)) { _, method, args ->
        when (method.name) {
            "openTun" -> throw AssertionError("Native startup regression must not open a VPN")
            "getInterfaces" -> object : NetworkInterfaceIterator {
                private var available = true
                override fun hasNext() = available
                override fun next(): NetworkInterface {
                    available = false
                    return NetworkInterface().apply {
                        index = 1; name = "lo"; mtu = 65536
                        flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING or OsConstants.IFF_LOOPBACK
                        addresses = BoxStringIterator(listOf("127.0.0.1/8"))
                    }
                }
            }
            "startDefaultInterfaceMonitor" -> {
                (args!![0] as InterfaceUpdateListener).updateDefaultInterface("lo", 1, false, false); null
            }
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                String::class.java -> ""
                else -> null
            }
        }
    } as PlatformInterface

    companion object {
        @BeforeClass @JvmStatic fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "warp-chain-startup-test").apply { mkdirs() }
            Libbox.setup(SetupOptions().apply {
                basePath = directory.absolutePath; workingPath = directory.absolutePath
                tempPath = directory.absolutePath; fixAndroidStack = true
            })
        }
    }
}
