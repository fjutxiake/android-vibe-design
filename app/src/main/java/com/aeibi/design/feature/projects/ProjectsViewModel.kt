package com.aeibi.design.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.Project
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectRepository.projects

    init {
        viewModelScope.launch { projectRepository.refresh() }
    }

    fun observeProject(id: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == id } }

    fun createProject(name: String, description: String, iconUri: String?) {
        viewModelScope.launch {
            runCatching { projectRepository.createProject(name, description, iconUri) }
        }
    }

    fun updateProject(id: String, name: String, description: String, iconUri: String?) {
        viewModelScope.launch {
            runCatching { projectRepository.updateProject(id, name, description, iconUri) }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.deleteSessionsForProject(id) }
            runCatching { projectRepository.deleteProject(id) }
        }
    }

    fun iconUri(project: Project): String? = projectRepository.iconUri(project)
}
