package com.aeibi.design.domain.repository

import com.aeibi.design.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
  fun observeProjects(): Flow<List<Project>>

  suspend fun createProject(project: Project)
}
