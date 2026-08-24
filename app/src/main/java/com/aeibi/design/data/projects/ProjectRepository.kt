package com.aeibi.design.data.projects

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ProjectRepository(
    private val projectsDir: File,
    private val iconCopier: IconCopier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun refresh() {
        _projects.value = withContext(ioDispatcher) { listProjects() }
    }

    suspend fun getProject(id: String): Project? = withContext(ioDispatcher) {
        readProject(File(projectsDir, id))
    }

    suspend fun createProject(name: String, description: String, iconUri: String?): Project =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val dir = File(projectsDir, id)
            check(dir.mkdirs()) { "无法创建项目目录" }
            val icon = iconCopier.copy(iconUri, dir)
            val project = Project(
                id = id,
                name = name,
                description = description,
                icon = icon,
                createdAt = now,
                updatedAt = now
            )
            writeProject(dir, project)
            _projects.value = listProjects()
            project
        }

    suspend fun updateProject(id: String, name: String, description: String, iconUri: String?): Project =
        withContext(ioDispatcher) {
            val dir = File(projectsDir, id)
            val existing = readProject(dir) ?: error("项目不存在: $id")
            val icon = if (iconUri != null) {
                val newIcon = iconCopier.copy(iconUri, dir)
                if (newIcon != null && newIcon != existing.icon) {
                    existing.icon?.let { File(dir, it).delete() }
                }
                newIcon ?: existing.icon
            } else {
                existing.icon
            }
            val updated = existing.copy(
                name = name,
                description = description,
                icon = icon,
                updatedAt = System.currentTimeMillis()
            )
            writeProject(dir, updated)
            _projects.value = listProjects()
            updated
        }

    suspend fun deleteProject(id: String) = withContext(ioDispatcher) {
        File(projectsDir, id).deleteRecursively()
        _projects.value = listProjects()
    }

    fun iconUri(project: Project): String? =
        project.icon?.let { File(projectsDir, project.id).resolve(it).toURI().toString() }

    private fun listProjects(): List<Project> = projectsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { readProject(it) }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    private fun readProject(dir: File): Project? = runCatching {
        val file = File(dir, PROJECT_JSON)
        if (!file.exists()) return@runCatching null
        json.decodeFromString(Project.serializer(), file.readText())
    }.getOrNull()

    private fun writeProject(dir: File, project: Project) {
        File(dir, PROJECT_JSON).writeText(json.encodeToString(Project.serializer(), project))
    }

    private companion object {
        const val PROJECT_JSON = "project.json"
    }
}
