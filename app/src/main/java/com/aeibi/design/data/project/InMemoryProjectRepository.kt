package com.aeibi.design.data.project

import com.aeibi.design.domain.model.Project
import com.aeibi.design.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryProjectRepository(
  initialProjects: List<Project> = defaultProjects(),
) : ProjectRepository {
  private val projects = MutableStateFlow(initialProjects)

  override fun observeProjects(): Flow<List<Project>> = projects.asStateFlow()

  override suspend fun createProject(project: Project) {
    projects.value = listOf(project) + projects.value
  }

  companion object {
    fun defaultProjects(): List<Project> {
      val now = System.currentTimeMillis()
      return listOf(
        Project(
          id = "daily-growth",
          name = "日常发芽",
          description = "不焦虑的日常习惯记录",
          updatedAt = now,
        ),
        Project(
          id = "weekend-trip",
          name = "周末去哪",
          description = "根据心情生成短途路线",
          updatedAt = now - DAY_MILLIS,
        ),
        Project(
          id = "focus-timer",
          name = "专注计时器",
          description = "把大任务切成可完成的小段",
          updatedAt = now - 5 * DAY_MILLIS,
        ),
      )
    }

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
  }
}

object ProjectRepositoryProvider {
  val instance: ProjectRepository by lazy { InMemoryProjectRepository() }
}
