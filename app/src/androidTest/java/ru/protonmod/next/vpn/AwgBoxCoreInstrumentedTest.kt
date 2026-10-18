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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native smoke tests: parse real generated configs without opening a VPN or sockets. */
@RunWith(AndroidJUnit4::class)
class AwgBoxCoreInstrumentedTest {
    private val generator = AwgBoxConfigGeneratorImpl(IpSubnetCalculatorImpl())

    @Test
    fun pinnedCoreLoadsOnAndroid() {
        assertEquals("1.15.0-alpha.12-awgm.33", Libbox.version())
    }

    @Test
    fun nativeCoreAcceptsAwgConfiguration() {
        Libbox.checkConfig(config())
    }

    @Test
    fun nativeCoreAcceptsVlessChain() {
        Libbox.checkConfig(config(
            proxyChain = "vless://123e4567-e89b-12d3-a456-426614174000@192.0.2.20:443?encryption=none"
        ))
    }

    @Test
    fun nativeCoreAcceptsTorConfiguration() {
        Libbox.checkConfig(config(tor = true))
    }

    private fun config(proxyChain: String? = null, tor: Boolean = false): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return generator.buildConfig(
            serverPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            privateKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.10",
            port = 443,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 4, jmin = 40, jmax = 70, s1 = 20, s2 = 30,
                s3 = 40, s4 = 50, h1 = "1-3", h2 = "4", h3 = "5", h4 = "6",
                i1 = "<b 0x01>", i2 = "<r 8>"
            ),
            proxyChainConfig = proxyChain,
            torModeEnabled = tor,
            torDataDirectory = File(context.cacheDir, "awgbox-test-tor").absolutePath,
            torExecutablePath = File(context.applicationInfo.nativeLibraryDir, "libtor.so").absolutePath
        )
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupCore() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "awgbox-native-tests")
            check(directory.mkdirs() || directory.isDirectory)
            Libbox.setup(SetupOptions().apply {
                basePath = directory.absolutePath
                workingPath = directory.absolutePath
                tempPath = directory.absolutePath
                fixAndroidStack = true
            })
        }
    }
}
