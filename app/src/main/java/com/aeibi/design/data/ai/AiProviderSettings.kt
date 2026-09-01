package com.aeibi.design.data.ai

import com.aeibi.design.ai.provider.ProviderConfig

data class AiProviderSettings(
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null
)
