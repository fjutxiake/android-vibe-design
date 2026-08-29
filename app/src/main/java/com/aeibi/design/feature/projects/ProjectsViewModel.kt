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
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectRepository.projects

    init {
        viewModelScope.launch { projectRepository.refresh() }
    }

    fun observeProject(id: String): Flow<Project?> = projects.map { list -> list.firstOrNull { it.id == id } }

    fun createProject(name: String, description: String, iconUri: String?, onResult: (Result<Project>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { projectRepository.createProject(name, description, iconUri) })
        }
    }

    fun markInitialized(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { projectRepository.markInitialized(id) })
        }
    }

    fun updateProject(
        id: String,
        name: String,
        description: String,
        iconUri: String?,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(runCatching { projectRepository.updateProject(id, name, description, iconUri) }.map { })
        }
    }

    fun deleteProject(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            // sessions 删除时 FK 级联清掉全部消息,DB 内两表原子;
            // 项目目录(文件系统)仍在事务外,删除失败如实上报。
            runCatching { sessionRepository.deleteSessionsForProject(id) }
            onResult(runCatching { projectRepository.deleteProject(id) })
        }
    }
}
