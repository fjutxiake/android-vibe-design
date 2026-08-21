package com.aeibi.design.domain.model

data class Project(
  val id: String,
  val name: String,
  val description: String,
  val iconUri: String? = null,
  val updatedAt: Long,
)
