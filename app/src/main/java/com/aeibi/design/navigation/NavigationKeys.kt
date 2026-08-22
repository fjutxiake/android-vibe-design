package com.aeibi.design.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ProjectChat(
  val projectId: String,
  val sessionId: String? = null,
) : NavKey

@Serializable data object ProjectPicker : NavKey

@Serializable data class ProjectPreview(val projectId: String) : NavKey

@Serializable data class ProjectBuild(val projectId: String) : NavKey

@Serializable data class ProjectVersions(val projectId: String) : NavKey

@Serializable data class ProjectSettings(val projectId: String) : NavKey

@Serializable data object ApplicationSettings : NavKey
