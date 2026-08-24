package com.aeibi.design.data.projects

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val icon: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
