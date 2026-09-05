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

    /** 从 zip 导入初始化（导入模板/项目包）。 */
    fun initializeFromZip(id: String, uri: android.net.Uri, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { projectRepository.initializeFromZip(id, uri) })
        }
    }

    /** 导出 workspace 为 zip。 */
    fun exportWorkspace(id: String, outFile: java.io.File, onResult: (Result<java.io.File>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(
                runCatching {
                    projectRepository.exportWorkspace(id, outFile)
                    outFile
                }
            )
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
            // 会话清理是尽力而为:清理失败不该拦住项目删除,留下几条孤儿会话是可以接受的。
            runCatching { sessionRepository.deleteSessionsForProject(id) }
            onResult(runCatching { projectRepository.deleteProject(id) })
        }
    }
}
