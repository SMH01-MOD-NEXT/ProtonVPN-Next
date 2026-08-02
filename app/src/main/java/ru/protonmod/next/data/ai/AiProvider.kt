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

/** Providers shipped with the app. User-defined providers live in [AiCustomProviders]. */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val format: AiApiFormat,
    val models: List<String>
) {
    OPENAI(
        "openai",
        "OpenAI",
        "https://api.openai.com/v1/chat/completions",
        AiApiFormat.OPENAI,
        listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "o1-preview")
    ),
    GEMINI(
        "gemini",
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta",
        AiApiFormat.GEMINI,
        listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro")
    ),
    ANTHROPIC(
        "anthropic",
        "Anthropic Claude",
        "https://api.anthropic.com/v1/messages",
        AiApiFormat.ANTHROPIC,
        listOf("claude-opus-5", "claude-fable-5", "claude-sonnet-5")
    ),
    DEEPSEEK(
        "deepseek",
        "DeepSeek",
        "https://api.deepseek.com/chat/completions",
        AiApiFormat.OPENAI,
        listOf("deepseek-v4-pro", "deepseek-v4-flash")
    );

    fun getDefaultModel(): String = models.first()

    fun toConfig(): AiProviderConfig = AiProviderConfig(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        format = format,
        models = models,
        isCustom = false,
    )

    companion object {
        fun fromId(id: String): AiProvider = entries.find { it.id == id } ?: OPENAI

        val builtInConfigs: List<AiProviderConfig> get() = entries.map(AiProvider::toConfig)
    }
}
