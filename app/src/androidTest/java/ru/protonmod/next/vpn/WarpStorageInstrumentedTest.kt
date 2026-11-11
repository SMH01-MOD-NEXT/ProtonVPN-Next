/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.vpn

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.WarpPreferences
import ru.protonmod.next.data.repository.CustomConfigRepository
import ru.protonmod.next.data.repository.CustomConfig
import ru.protonmod.next.data.network.WarpEndpoints
import java.io.File
import java.util.UUID

/** No registration, sockets or VPN. Uses a disposable encrypted file, not the user's configs. */
@RunWith(AndroidJUnit4::class)
class WarpStorageInstrumentedTest {
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val conf get() = "[Interface]\nPrivateKey = $key\nAddress = 172.16.0.2/32\nDNS = 1.1.1.1\nMTU = 1280\n[Peer]\nPublicKey = $key\nEndpoint = engage.cloudflareclient.com:2408\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\nReserved = 1, 2, 3\n"
    @Test fun encryptedSingletonSurvivesReloadAndConcurrentReplacement() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "warp-storage-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir(): File = directory }
        try {
            val repository = CustomConfigRepository(context)
            val link = repository.normalize(conf)
            repository.save(CustomConfig(id = "mine", name = "Mine", link = link))
            coroutineScope { (1..6).map { port -> async {
                repository.saveWarp(repository.normalize(WarpEndpoints.apply(conf, port = WarpEndpoints.ports[port], profile = port % 4)))
            } }.awaitAll() }
            assertEquals(2, repository.configs.value.size)
            assertEquals(1, repository.configs.value.count { it.isWarp })
            val file = File(directory, "custom-configs.enc")
            val raw = file.readBytes().decodeToString()
            assertFalse(raw.contains(key)); assertFalse(raw.contains("PrivateKey")); assertFalse(raw.contains(link))
            val reopened = CustomConfigRepository(context)
            reopened.load()
            assertEquals(repository.configs.value, reopened.configs.value)
            assertTrue(reopened.resolveChain("custom://warp", true).startsWith(ImportedWireGuard.SCHEME))
            assertTrue(runCatching { reopened.resolveChain("custom://warp", false) }.isFailure)
        } finally { directory.deleteRecursively() }
    }
    @Test fun separateLocalParametersSurviveSettingsManagerRecreation() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsManager(target)
        val original = settings.warpPreferences.first()
        try {
            val wanted = WarpPreferences(true, "162.159.193.1", true, "warp.example.com", 500, 3)
            settings.setWarpPreferences(wanted)
            assertEquals(wanted, SettingsManager(target).warpPreferences.first())
        } finally { settings.setWarpPreferences(original) }
    }
}
