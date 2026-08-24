package com.aeibi.design.ai.provider

import com.aeibi.design.R
import jakarta.inject.Inject
import jakarta.inject.Singleton

const val OPENAI_PROVIDER_TYPE = "openai"

@Singleton
class OpenAiProvider @Inject constructor() : AiProvider {
    override val definition = ProviderDefinition(
        type = OPENAI_PROVIDER_TYPE,
        displayName = "OpenAI",
        iconRes = R.drawable.provider_openai,
        defaultEndpoint = "https://api.openai.com/v1",
        defaultModels = listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna")
    )
}
