package com.aeibi.design.feature.workspace

import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.runtimelogs.RuntimeLogEntry
import com.aeibi.design.data.runtimelogs.RuntimeLogStore
import com.aeibi.design.feature.preview.LocalStaticAssetLoader
import com.aeibi.design.feature.preview.LocalStaticFileServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal enum class PreviewStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}

internal data class PreviewUiState(
    val status: PreviewStatus = PreviewStatus.STOPPED,
    val url: Uri? = null,
    val errorMessage: String? = null,
    /** agent 回合内 reload_preview 请求计数——每次 +1，Pane 观察到先清日志再执行 reload。 */
    val reloadRequestTick: Int = 0
)

@Serializable
internal data class WorkspaceConfig(val preview: PreviewConfig = PreviewConfig())

@Serializable
internal data class PreviewConfig(
    val mode: String = "http-server",
    val root: String = ".",
    val entry: String = "index.html",
    val fallback: String? = "index.html"
)

@HiltViewModel
class ProjectWorkspaceViewModel internal constructor(
    private val projectRepository: ProjectRepository,
    private val fileServer: LocalStaticFileServer,
    private val assetLoader: LocalStaticAssetLoader,
    private val runtimeLogStore: RuntimeLogStore,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        projectRepository: ProjectRepository,
        @ApplicationContext context: Context,
        runtimeLogStore: RuntimeLogStore
    ) : this(
        projectRepository,
        LocalStaticFileServer(),
        LocalStaticAssetLoader(context),
        runtimeLogStore,
        Dispatchers.IO
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val _previewUiState = MutableStateFlow(PreviewUiState())
    internal val previewUiState: StateFlow<PreviewUiState> = _previewUiState.asStateFlow()
    private var projectId: String? = null

    fun startPreview(projectId: String) {
        if (_previewUiState.value.status !in listOf(PreviewStatus.STOPPED, PreviewStatus.FAILED)) return
        this.projectId = projectId
        _previewUiState.value = PreviewUiState(status = PreviewStatus.STARTING)

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { startBackend(projectId) }
            }.onSuccess { url ->
                _previewUiState.value = PreviewUiState(PreviewStatus.RUNNING, url)
            }.onFailure { error ->
                withContext(ioDispatcher) { runCatching { stopBackends() } }
                _previewUiState.value = PreviewUiState(
                    status = PreviewStatus.FAILED,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun stopPreview() {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        _previewUiState.value = _previewUiState.value.copy(status = PreviewStatus.STOPPING)

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { stopBackends() }
            }.onSuccess {
                _previewUiState.value = PreviewUiState()
            }.onFailure { error ->
                _previewUiState.value = PreviewUiState(
                    status = PreviewStatus.FAILED,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun shouldInterceptRequest(uri: Uri): WebResourceResponse? = assetLoader.shouldInterceptRequest(uri)

    /** Console 面板展示源——与 agent 的 read_runtime_logs 同一份数据（单一来源）。 */
    fun observeConsoleLogs(): Flow<List<RuntimeLogEntry>> {
        val activeProjectId = projectId ?: return flowOf(emptyList())
        return runtimeLogStore.observe(activeProjectId)
    }

    internal fun recordConsoleMessage(message: ConsoleMessage) {
        val running = _previewUiState.value.status == PreviewStatus.RUNNING
        if (!running) return
        val source = if (message.sourceId().isNotEmpty()) {
            "${message.sourceId()}:${message.lineNumber()}"
        } else {
            ""
        }
        recordLogEntry(
            level = message.messageLevel().name,
            message = message.message(),
            source = source
        )
    }

    /** 主 frame 加载失败（网络/HTTP）——写入日志（Console 可见、agent 可读），不打断用户。 */
    internal fun recordPageError(code: Int, description: String, url: String) {
        recordLogEntry(
            level = "ERROR",
            message = "Preview page failed to load: $description (code $code) at $url",
            source = url
        )
    }

    /** 刷新/重新加载前调用：清空当前日志（旧日志不残留——agent 修复后重载不会读到旧错误）。 */
    internal fun clearLogs() {
        projectId?.let(runtimeLogStore::clear)
    }

    /** agent 回合内请求刷新预览（reload_preview 工具）——Pane 观察到先清日志再 reload。 */
    fun onPreviewReloadRequested() {
        _previewUiState.update { it.copy(reloadRequestTick = it.reloadRequestTick + 1) }
    }

    private fun recordLogEntry(level: String, message: String, source: String) {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        runtimeLogStore.record(
            projectId = projectId ?: return,
            entry = RuntimeLogEntry(
                level = level,
                message = message,
                source = source,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun startBackend(projectId: String): Uri {
        val workspace = projectRepository.workspaceDirectory(projectId).toPath().normalize()
        val configFile = File(workspace.toFile(), CONFIG_FILE)
        val config = if (configFile.exists()) {
            json.decodeFromString<WorkspaceConfig>(configFile.readText())
        } else {
            WorkspaceConfig()
        }
        val previewRoot = workspace.resolve(config.preview.root).normalize()
        require(previewRoot.startsWith(workspace)) {
            "Preview root must stay inside the workspace"
        }

        return when (config.preview.mode) {
            "asset-loader" -> assetLoader.start(previewRoot, config.preview.entry)
            "http-server" -> fileServer.start(previewRoot, 0, config.preview.fallback).toString().toUri()
            else -> error("Unsupported preview mode: ${config.preview.mode}")
        }
    }

    private fun stopBackends() {
        try {
            fileServer.stop()
        } finally {
            assetLoader.stop()
        }
    }

    override fun onCleared() {
        runCatching { stopBackends() }
    }

    private companion object {
        const val CONFIG_FILE = "vibe.config.json"
    }
}
