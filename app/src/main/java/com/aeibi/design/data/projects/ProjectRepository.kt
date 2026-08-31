package com.aeibi.design.data.projects

import android.content.ContentResolver
import android.content.res.AssetManager
import androidx.core.net.toUri
import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepository(
    private val projectsDir: File,
    private val contentResolver: ContentResolver,
    private val assets: AssetManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun workspaceDirectory(projectId: String): File = File(File(projectsDir, projectId), WORKSPACE_DIR)

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
            try {
                check(File(dir, WORKSPACE_DIR).mkdir()) { "无法创建项目工作区目录" }

                val iconFileName = iconUri?.let { writeIcon(it, dir) }
                val metadata = ProjectMetadata(
                    name = name,
                    description = description,
                    createdAt = now,
                    updatedAt = now,
                    iconFileName = iconFileName,
                    isInitialized = false
                )
                writeMetadata(dir, metadata)
                _projects.value = listProjects()
                metadata.toProject(id, dir)
            } catch (error: Exception) {
                dir.deleteRecursively()
                throw error
            }
        }

    suspend fun updateProject(id: String, name: String, description: String, iconUri: String?): Project =
        withContext(ioDispatcher) {
            val dir = File(projectsDir, id)
            val existing = readMetadata(dir) ?: error("项目不存在: $id")
            val iconFileName = iconUri?.let { writeIcon(it, dir) } ?: existing.resolveIconFileName(dir)
            val metadata = ProjectMetadata(
                name = name,
                description = description,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis(),
                iconFileName = iconFileName,
                isInitialized = existing.isInitialized
            )
            writeMetadata(dir, metadata)
            _projects.value = listProjects()
            metadata.toProject(id, dir)
        }

    suspend fun deleteProject(id: String) = withContext(ioDispatcher) {
        val dir = File(projectsDir, id)
        // deleteRecursively() 删不动时只返回 false。目录已经不在就当删成功(重复删除是幂等的),
        // 但目录还在就说明真的删失败了,必须让调用方知道,不能假装删掉了。
        if (!dir.deleteRecursively() && dir.exists()) {
            throw IOException("无法删除项目目录: ${dir.path}")
        }
        _projects.value = listProjects()
    }

    suspend fun markInitialized(id: String) = withContext(ioDispatcher) {
        val dir = File(projectsDir, id)
        val existing = readMetadata(dir) ?: error("Project not found: $id")
        if (!existing.isInitialized) {
            val workspace = File(dir, WORKSPACE_DIR)
            if (!workspace.deleteRecursively() && workspace.exists()) {
                throw IOException("Could not clear workspace: ${workspace.path}")
            }
            check(workspace.mkdir()) { "Could not create workspace: ${workspace.path}" }
            val pendingWorkspace = File(dir, PENDING_WORKSPACE_DIR)
            if (!pendingWorkspace.deleteRecursively() && pendingWorkspace.exists()) {
                throw IOException("Could not clear pending workspace: ${pendingWorkspace.path}")
            }
            writeMetadata(
                dir,
                existing.copy(
                    updatedAt = System.currentTimeMillis(),
                    isInitialized = true
                )
            )
            _projects.value = listProjects()
        }
    }

    suspend fun initializeFromTemplate(id: String, workspaceAssetPath: String) = withContext(ioDispatcher) {
        val dir = File(projectsDir, id)
        val existing = readMetadata(dir) ?: error("Project not found: $id")
        check(!existing.isInitialized) { "Project is already initialized: $id" }

        val pendingWorkspace = File(dir, PENDING_WORKSPACE_DIR)
        if (!pendingWorkspace.deleteRecursively() && pendingWorkspace.exists()) {
            throw IOException("Could not clear pending workspace: ${pendingWorkspace.path}")
        }

        try {
            copyAssetTree(workspaceAssetPath, pendingWorkspace)
            if (!pendingWorkspace.isDirectory) {
                throw IOException("Template workspace is not a directory: $workspaceAssetPath")
            }
            replaceWorkspace(dir, pendingWorkspace)
            writeMetadata(
                dir,
                existing.copy(
                    updatedAt = System.currentTimeMillis(),
                    isInitialized = true
                )
            )
            _projects.value = listProjects()
        } catch (error: Exception) {
            pendingWorkspace.deleteRecursively()
            throw error
        }
    }

    private fun listProjects(): List<Project> = projectsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { readProject(it) }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    private fun readProject(dir: File): Project? = readMetadata(dir)?.toProject(dir.name, dir)

    private fun readMetadata(dir: File): ProjectMetadata? = runCatching {
        val file = AtomicFile(File(dir, PROJECT_JSON))
        json.decodeFromString<ProjectMetadata>(file.readFully().decodeToString())
    }.getOrNull()

    private fun writeMetadata(dir: File, metadata: ProjectMetadata) {
        val file = AtomicFile(File(dir, PROJECT_JSON))
        val output = file.startWrite()
        try {
            output.write(json.encodeToString(metadata).encodeToByteArray())
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun writeIcon(uri: String, dir: File): String {
        val fileName = "icon-${UUID.randomUUID()}.png"
        val iconFile = File(dir, fileName)
        val file = AtomicFile(iconFile)
        val input = contentResolver.openInputStream(uri.toUri())
            ?: throw IOException("无法读取项目图标")
        input.use { source ->
            val output = file.startWrite()
            try {
                source.copyTo(output)
                file.finishWrite(output)
                if (!iconFile.isFile) throw IOException("无法保存项目图标: ${iconFile.path}")
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }
        return fileName
    }

    private fun copyAssetTree(sourcePath: String, target: File) {
        val children = assets.list(sourcePath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(sourcePath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        check(target.mkdirs() || target.isDirectory) {
            "Could not create workspace directory: ${target.path}"
        }
        children.forEach { name ->
            copyAssetTree("$sourcePath/$name", File(target, name))
        }
    }

    private fun replaceWorkspace(projectDir: File, pendingWorkspace: File) {
        val workspace = File(projectDir, WORKSPACE_DIR)
        if (!workspace.deleteRecursively() && workspace.exists()) {
            throw IOException("Could not replace workspace: ${workspace.path}")
        }

        try {
            try {
                Files.move(
                    pendingWorkspace.toPath(),
                    workspace.toPath(),
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pendingWorkspace.toPath(), workspace.toPath())
            }
        } catch (error: Exception) {
            workspace.mkdirs()
            throw error
        }
    }

    private fun ProjectMetadata.toProject(id: String, dir: File): Project {
        val iconFile = resolveIconFileName(dir)?.let { File(dir, it) }
        return Project(
            id = id,
            name = name,
            description = description,
            iconUri = iconFile?.toURI()?.toString(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isInitialized = isInitialized
        )
    }

    private fun ProjectMetadata.resolveIconFileName(dir: File): String? = iconFileName
        ?.takeIf { File(it).name == it && File(dir, it).isFile }

    private companion object {
        const val PROJECT_JSON = "project.json"
        const val WORKSPACE_DIR = "workspace"
        const val PENDING_WORKSPACE_DIR = "workspace.pending"
    }
}

@Serializable
private data class ProjectMetadata(
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val iconFileName: String? = null,
    val isInitialized: Boolean = true
)
