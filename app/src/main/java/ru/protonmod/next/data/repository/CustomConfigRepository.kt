/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import ru.protonmod.next.vpn.ImportedWireGuard
import ru.protonmod.next.vpn.ProxyLinkParser

@Serializable
data class CustomConfig(val id: String = UUID.randomUUID().toString(), val name: String,
    val link: String, val country: String = "", val city: String = "",
    val kind: String = "custom", val previousIds: List<String> = emptyList()) {
    val isWarp: Boolean get() = kind == "warp"
}

/** Secret config bytes are AES-GCM encrypted with a non-exportable Android Keystore key.
 * noBackupFilesDir prevents a restored ciphertext from becoming detached from its device key. */
@Singleton
class CustomConfigRepository @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "custom-configs.enc"))
    private val mutex = Mutex()
    private val _configs = MutableStateFlow<List<CustomConfig>>(emptyList())
    val configs = _configs.asStateFlow()
    private var loaded = false
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("custom-vpn-configs", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("custom-vpn-configs", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { loadLocked() } }
    private fun loadLocked() {
        if (loaded) return
        if (file.baseFile.exists()) {
            val data = file.readFully(); require(data.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            _configs.value = CustomConfigRules.migrate(Json.decodeFromString(cipher.doFinal(data.copyOfRange(12, data.size)).decodeToString()))
        }
        loaded = true
    }
    suspend fun save(config: CustomConfig) = mutate { existing ->
        require(config.name.isNotBlank() && config.name.length <= 100)
        require(config.country.isEmpty() || config.country in java.util.Locale.getISOCountries())
        require(config.city.length <= 100 && ProxyLinkParser.isValid(config.link))
        require(existing.size < 100 || existing.any { it.id == config.id })
        require(!config.isWarp && config.id != CustomConfigRules.WARP_ID)
        existing.filterNot { it.id == config.id } + config
    }
    suspend fun saveWarp(link: String) = mutate { existing ->
        require(ProxyLinkParser.isValid(link))
        CustomConfigRules.replaceWarp(existing, link)
    }
    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }
    private suspend fun mutate(transform: (List<CustomConfig>) -> List<CustomConfig>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val next = transform(_configs.value)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.iv + cipher.doFinal(Json.encodeToString(next).toByteArray())
            val stream = file.startWrite()
            try { stream.write(data); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
            _configs.value = next
        }
    }
    suspend fun resolveChain(raw: String, warpEnabled: Boolean = true): String {
        load()
        return CustomConfigRules.resolveChain(configs.value, raw, warpEnabled)
    }
    fun normalize(raw: String): String = if (raw.trimStart().startsWith("[Interface]")) ImportedWireGuard.encode(raw.trim())
        else raw.trim().also { require(ProxyLinkParser.isValid(it) && '\n' !in it) }
}
