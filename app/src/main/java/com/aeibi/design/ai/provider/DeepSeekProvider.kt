package com.aeibi.design.ai.provider

import com.aeibi.design.R
import jakarta.inject.Inject
import jakarta.inject.Singleton

const val DEEPSEEK_PROVIDER_TYPE = "deepseek"

@Singleton
class DeepSeekProvider @Inject constructor() : AiProvider {
    override val definition = ProviderDefinition(
        type = DEEPSEEK_PROVIDER_TYPE,
        displayName = "DeepSeek",
        iconRes = R.drawable.provider_deepseek,
        defaultEndpoint = "https://api.deepseek.com",
        defaultModels = listOf("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-flash-vision-exp")
    )
}
