package ru.protonmod.next.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderConfigTest {

    private fun custom(baseUrl: String, format: AiApiFormat) = AiProviderConfig(
        id = "custom:test",
        displayName = "Test",
        baseUrl = baseUrl,
        format = format,
        isCustom = true,
    )

    @Test
    fun `openai root gets chat and models endpoints appended`() {
        val provider = custom("https://openrouter.ai/api/v1/", AiApiFormat.OPENAI)

        assertEquals("https://openrouter.ai/api/v1/chat/completions", AiEndpoints.chat(provider, "m", "k"))
        assertEquals("https://openrouter.ai/api/v1/models", AiEndpoints.models(provider, "k"))
    }

    @Test
    fun `full openai chat endpoint is kept as is`() {
        val provider = custom("https://api.openai.com/v1/chat/completions", AiApiFormat.OPENAI)

        assertEquals("https://api.openai.com/v1/chat/completions", AiEndpoints.chat(provider, "m", "k"))
        assertEquals("https://api.openai.com/v1/models", AiEndpoints.models(provider, "k"))
    }

    @Test
    fun `anthropic endpoints are derived from the api root`() {
        val provider = custom("https://api.anthropic.com/v1/messages", AiApiFormat.ANTHROPIC)

        assertEquals("https://api.anthropic.com/v1/messages", AiEndpoints.chat(provider, "m", "k"))
        assertEquals("https://api.anthropic.com/v1/models", AiEndpoints.models(provider, "k"))
    }

    @Test
    fun `gemini endpoints carry the model and api key`() {
        val provider = AiProvider.GEMINI.toConfig()

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro:generateContent?key=secret",
            AiEndpoints.chat(provider, "gemini-3.1-pro", "secret")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?key=secret",
            AiEndpoints.models(provider, "secret")
        )
    }

    @Test
    fun `base url must be absolute`() {
        assertTrue(AiEndpoints.isValidBaseUrl("https://api.example.com/v1"))
        assertFalse(AiEndpoints.isValidBaseUrl("api.example.com/v1"))
        assertFalse(AiEndpoints.isValidBaseUrl("https://localhost"))
        assertFalse(AiEndpoints.isValidBaseUrl("  "))
    }

    @Test
    fun `custom providers survive a serialization round trip`() {
        val providers = listOf(
            custom("https://api.example.com/v1", AiApiFormat.OPENAI).copy(models = listOf("a", "b")),
            custom("https://llm.internal/v1", AiApiFormat.ANTHROPIC).copy(id = "custom:internal"),
        )

        val restored = AiCustomProviders.decode(AiCustomProviders.encode(providers))

        assertEquals(providers, restored)
    }

    @Test
    fun `malformed stored json degrades to an empty list`() {
        assertEquals(emptyList<AiProviderConfig>(), AiCustomProviders.decode("not json"))
        assertEquals(emptyList<AiProviderConfig>(), AiCustomProviders.decode(""))
    }

    @Test
    fun `ids are slugged and de-duplicated`() {
        val first = AiCustomProviders.newId("My Local LLM", emptySet())
        val second = AiCustomProviders.newId("My Local LLM", setOf(first))

        assertEquals("custom:my-local-llm", first)
        assertEquals("custom:my-local-llm-2", second)
    }

    @Test
    fun `openai model catalogue is parsed`() {
        val body = """{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}"""

        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), AiModelListParser.parse(AiApiFormat.OPENAI, body))
    }

    @Test
    fun `gemini model catalogue strips the models prefix`() {
        val body = """{"models":[{"name":"models/gemini-2.0-flash"}]}"""

        assertEquals(listOf("gemini-2.0-flash"), AiModelListParser.parse(AiApiFormat.GEMINI, body))
    }

    @Test
    fun `invalid model catalogue yields no models`() {
        assertEquals(emptyList<String>(), AiModelListParser.parse(AiApiFormat.OPENAI, "<html>oops</html>"))
    }
}
