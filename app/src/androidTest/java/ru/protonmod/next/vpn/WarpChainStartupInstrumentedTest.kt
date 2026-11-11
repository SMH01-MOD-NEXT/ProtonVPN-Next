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
