package com.aeibi.design.ai.provider

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
    private val config = ProviderConfig(
        id = "provider",
        providerType = OPENAI_PROVIDER_TYPE,
        displayName = "OpenAI",
        endpoint = "https://api.openai.com/v1",
        models = listOf("model")
    )

    @Test
    fun openAiUsesVersionedBaseUrlWithoutDuplicatingVersionPath() {
        val settings = OpenAiProvider(unusedHttpClientFactory).settings(config)

        assertEquals("https://api.openai.com/v1", settings.baseUrl)
        assertEquals("chat/completions", settings.chatCompletionsPath)
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            settings.baseUrl + "/" + settings.chatCompletionsPath
        )
    }

    @Test
    fun providersCreateModelsWithExpectedCapabilities() {
        val openAiModel = OpenAiProvider(unusedHttpClientFactory).createModel("openai-model")
        val deepSeekModel = DeepSeekProvider(unusedHttpClientFactory).createModel("deepseek-model")

        assertEquals(LLMProvider.OpenAI, openAiModel.provider)
        assertEquals(LLMProvider.DeepSeek, deepSeekModel.provider)
        assertTrue(openAiModel.supports(LLMCapability.Tools))
        assertTrue(openAiModel.supports(LLMCapability.ToolChoice))
        assertTrue(openAiModel.supports(LLMCapability.OpenAIEndpoint.Completions))
        assertTrue(deepSeekModel.supports(LLMCapability.Tools))
        assertTrue(deepSeekModel.supports(LLMCapability.ToolChoice))
    }

    private val unusedHttpClientFactory = object : KoogHttpClient.Factory {
        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): KoogHttpClient = error("HTTP client is not used by these tests")
    }
}
