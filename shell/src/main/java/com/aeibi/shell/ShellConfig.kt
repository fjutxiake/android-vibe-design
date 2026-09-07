package com.aeibi.shell

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceConfig(val build: WebRuntimeConfig = WebRuntimeConfig())

@Serializable
data class WebRuntimeConfig(
    val mode: String = "asset-loader",
    val root: String = ".",
    val entry: String = "index.html",
    val fallback: String? = "index.html"
)
