package com.aeibi.design.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aeibi.design.domain.model.Project
import com.aeibi.design.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProjectsUiState(
  val projects: List<Project> = emptyList(),
)

class ProjectsViewModel(
  private val projectRepository: ProjectRepository,
) : ViewModel() {
  val uiState: StateFlow<ProjectsUiState> =
    projectRepository
      .observeProjects()
      .map(::ProjectsUiState)
      .stateIn(viewModelScope, SharingStarted.Eagerly, ProjectsUiState())

  fun createProject(name: String, description: String, iconUri: String?) {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return

    viewModelScope.launch {
      projectRepository.createProject(
        Project(
          id = UUID.randomUUID().toString(),
          name = normalizedName,
          description = description.trim(),
          iconUri = iconUri,
          updatedAt = System.currentTimeMillis(),
        ),
      )
    }
  }
}

class ProjectsViewModelFactory(
  private val projectRepository: ProjectRepository,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(ProjectsViewModel::class.java))
    return ProjectsViewModel(projectRepository) as T
  }
}
