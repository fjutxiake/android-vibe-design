package com.aeibi.design.data.templates

data class Template(
    val id: String,
    val category: String,
    val name: String,
    val description: String,
    val coverAssetPath: String,
    val previewAssetPaths: List<String>,
    val readmeAssetPath: String?,
    val workspaceAssetPath: String
)
