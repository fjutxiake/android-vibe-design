package com.aeibi.design.ai.provider

import androidx.annotation.DrawableRes

data class ProviderDefinition(
    val type: String,
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val defaultEndpoint: String,
    val defaultModels: List<String> = emptyList()
)
