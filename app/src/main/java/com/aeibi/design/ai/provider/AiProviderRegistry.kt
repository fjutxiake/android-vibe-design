package com.aeibi.design.ai.provider

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AiProviderRegistry @Inject constructor(
    openAiCompatibleProvider: OpenAiCompatibleProvider,
    deepSeekProvider: DeepSeekProvider
) {
    private val providers: List<AiProvider> = listOf(deepSeekProvider, openAiCompatibleProvider)

    val definitions: List<ProviderDefinition> = providers.map(AiProvider::definition)
}
