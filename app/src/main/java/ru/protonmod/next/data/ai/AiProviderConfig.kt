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

package ru.protonmod.next.data.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Wire format a provider speaks. Custom providers must be compatible with one of these. */
enum class AiApiFormat(val id: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GEMINI("gemini");

    companion object {
        /** Custom providers may only declare formats that can be described by a single base URL. */
        val userSelectable: List<AiApiFormat> = listOf(OPENAI, ANTHROPIC)

        fun fromId(id: String?): AiApiFormat =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: OPENAI
    }
}

/**
 * A provider the app can talk to. Built-in providers are derived from [AiProvider]; user-defined
 * ones are stored as JSON in settings and behave identically everywhere else in the app.
 */
data class AiProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val format: AiApiFormat,
    val models: List<String> = emptyList(),
    val isCustom: Boolean = false,
) {
    fun getDefaultModel(): String = models.firstOrNull().orEmpty()
}

@Serializable
internal data class CustomAiProviderDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val format: String,
    val models: List<String> = emptyList(),
)

/** Serialization and id handling for user-defined providers. */
object AiCustomProviders {
    const val ID_PREFIX = "custom:"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val idSanitizer = Regex("[^a-z0-9]+")

    fun decode(raw: String): List<AiProviderConfig> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CustomAiProviderDto>>(raw) }
            .getOrDefault(emptyList())
            .mapNotNull { dto ->
                val name = dto.name.trim()
                val baseUrl = dto.baseUrl.trim()
                if (dto.id.isBlank() || name.isEmpty() || baseUrl.isEmpty()) return@mapNotNull null
                AiProviderConfig(
                    id = dto.id,
                    displayName = name,
                    baseUrl = baseUrl,
                    format = AiApiFormat.fromId(dto.format),
                    models = dto.models.map(String::trim).filter(String::isNotEmpty).distinct(),
                    isCustom = true,
                )
            }
    }

    fun encode(providers: List<AiProviderConfig>): String = json.encodeToString(
        providers.map { provider ->
            CustomAiProviderDto(
                id = provider.id,
                name = provider.displayName,
                baseUrl = provider.baseUrl,
                format = provider.format.id,
                models = provider.models,
            )
        }
    )

    /** Builds a stable, collision-free id from the display name. */
    fun newId(displayName: String, existingIds: Set<String>): String {
        val slug = idSanitizer.replace(displayName.lowercase(Locale.ROOT), "-").trim('-').ifEmpty { "provider" }
        val base = ID_PREFIX + slug
        if (base !in existingIds) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in existingIds }
    }
}

/**
 * Resolves request URLs from a provider base URL. Users typically paste an API root such as
 * `https://openrouter.ai/api/v1`, but a full chat endpoint is accepted as well.
 */
object AiEndpoints {
    fun chat(config: AiProviderConfig, model: String, apiKey: String): String = when (config.format) {
        AiApiFormat.OPENAI -> withSuffix(config.baseUrl, "/chat/completions")
        AiApiFormat.ANTHROPIC -> withSuffix(config.baseUrl, "/messages")
        AiApiFormat.GEMINI -> "${root(config)}/models/$model:generateContent?key=$apiKey"
    }

    fun models(config: AiProviderConfig, apiKey: String): String = when (config.format) {
        AiApiFormat.OPENAI, AiApiFormat.ANTHROPIC -> "${root(config)}/models"
        AiApiFormat.GEMINI -> "${root(config)}/models?key=$apiKey"
    }

    /** The API root, i.e. the base URL without a known endpoint path. */
    fun root(config: AiProviderConfig): String = root(config.baseUrl)

    fun root(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val knownSuffixes = listOf("/chat/completions", "/messages", "/models", "/completions")
        return knownSuffixes.firstOrNull { trimmed.endsWith(it, ignoreCase = true) }
            ?.let { trimmed.dropLast(it.length) }
            ?.trimEnd('/')
            ?: trimmed
    }

    private fun withSuffix(baseUrl: String, suffix: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith(suffix, ignoreCase = true)) trimmed else root(trimmed) + suffix
    }

    /** Accepts only absolute http(s) URLs so a typo cannot turn into a relative OkHttp crash. */
    fun isValidBaseUrl(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/').contains('.')
    }
}

/** Parses the model catalogue returned by a provider so models can be discovered like an IDE does. */
object AiModelListParser {
    fun parse(format: AiApiFormat, body: String): List<String> = runCatching {
        val arrayKey = when (format) {
            AiApiFormat.OPENAI, AiApiFormat.ANTHROPIC -> "data"
            AiApiFormat.GEMINI -> "models"
        }
        val array = Json.parseToJsonElement(body).jsonObject[arrayKey]?.jsonArray ?: return emptyList()
        array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item["id"]?.jsonPrimitive?.contentOrNull
                ?: item["name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            id.trim().removePrefix("models/").takeIf(String::isNotEmpty)
        }.distinct().sorted()
    }.getOrDefault(emptyList())
}
