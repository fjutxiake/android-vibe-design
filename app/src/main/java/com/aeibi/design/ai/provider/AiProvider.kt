package com.aeibi.design.ai.provider

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel

interface AiProvider {
    val definition: ProviderDefinition

    fun createClient(config: ProviderConfig, apiKey: String): LLMClient

    fun createModel(modelId: String): LLModel
}
