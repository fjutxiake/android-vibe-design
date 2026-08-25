package com.aeibi.design.data.projects

data class Project(
    val id: String,
    val name: String,
    val description: String,
    val iconUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
