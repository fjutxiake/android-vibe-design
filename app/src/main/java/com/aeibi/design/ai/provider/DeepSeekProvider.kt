package com.aeibi.design.ai.provider

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.aeibi.design.R
import jakarta.inject.Inject
import jakarta.inject.Singleton

const val DEEPSEEK_PROVIDER_TYPE = "deepseek"

@Singleton
class DeepSeekProvider @Inject constructor(private val httpClientFactory: KoogHttpClient.Factory) : AiProvider {
    override val definition = ProviderDefinition(
        type = DEEPSEEK_PROVIDER_TYPE,
        displayName = "DeepSeek",
        iconRes = R.drawable.provider_deepseek,
        defaultEndpoint = "https://api.deepseek.com",
        defaultModels = listOf("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-flash-vision-exp")
    )

    override fun createClient(config: ProviderConfig, apiKey: String): LLMClient = DeepSeekLLMClient(
        apiKey = apiKey,
        settings = DeepSeekClientSettings(baseUrl = config.endpoint),
        httpClientFactory = httpClientFactory
    )

    override fun createModel(modelId: String): LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = modelId,
        capabilities = COMMON_CAPABILITIES
    )
}
