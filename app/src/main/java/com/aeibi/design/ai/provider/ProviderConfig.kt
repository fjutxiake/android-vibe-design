package com.aeibi.design.ai.provider

data class ProviderConfig(
    val id: String,
    val providerType: String,
    val displayName: String,
    val endpoint: String,
    val models: List<String>
)
