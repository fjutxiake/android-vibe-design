package com.aeibi.design.ai.provider

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.aeibi.design.R
import jakarta.inject.Inject
import jakarta.inject.Singleton

const val OPENAI_PROVIDER_TYPE = "openai"

@Singleton
class OpenAiProvider @Inject constructor(private val httpClientFactory: KoogHttpClient.Factory) : AiProvider {
    override val definition = ProviderDefinition(
        type = OPENAI_PROVIDER_TYPE,
        displayName = "OpenAI",
        iconRes = R.drawable.provider_openai,
        defaultEndpoint = "https://api.openai.com/v1",
        defaultModels = listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna")
    )

    override fun createClient(config: ProviderConfig, apiKey: String): LLMClient = OpenAILLMClient(
        apiKey = apiKey,
        settings = settings(config),
        httpClientFactory = httpClientFactory
    )

    override fun createModel(modelId: String): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = modelId,
        capabilities = COMMON_CAPABILITIES + LLMCapability.OpenAIEndpoint.Completions
    )

    internal fun settings(config: ProviderConfig): OpenAIClientSettings = OpenAIClientSettings(
        baseUrl = config.endpoint,
        chatCompletionsPath = "chat/completions",
        responsesAPIPath = "responses",
        embeddingsPath = "embeddings",
        moderationsPath = "moderations",
        modelsPath = "models"
    )
}

internal val COMMON_CAPABILITIES = listOf(
    LLMCapability.Completion,
    LLMCapability.Tools,
    LLMCapability.ToolChoice
)
