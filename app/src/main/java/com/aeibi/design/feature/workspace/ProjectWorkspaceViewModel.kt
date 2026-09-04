package com.aeibi.design.feature.workspace

import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebResourceResponse
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** 主 frame 页面加载失败（网络错误 / HTTP 错误）。 */
internal data class PreviewPageError(val code: Int, val description: String, val url: String)

internal data class PreviewUiState(
    val status: PreviewStatus = PreviewStatus.STOPPED,
    val url: Uri? = null,
    val errorMessage: String? = null,
    val consoleMessages: List<ConsoleMessage> = emptyList(),
    val pageError: PreviewPageError? = null,
    /** agent 每完成一个回合 +1——预览据此知道工作区内容可能已变。 */
    val contentVersion: Int = 0
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

    internal fun recordPageError(code: Int, description: String, url: String) {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        if (_previewUiState.value.pageError != null) return
        _previewUiState.update { it.copy(pageError = PreviewPageError(code, description, url)) }
    }

    /** 主 frame 加载完成——页面恢复则清除残留错误。 */
    internal fun onPageFinished() {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        _previewUiState.update { it.copy(pageError = null) }
    }

    fun dismissPageError() {
        _previewUiState.update { it.copy(pageError = null) }
    }

    /** agent 回合完成——工作区内容可能已变，预览需要按新版本重新加载。 */
    fun onAgentTurnCompleted() {
        _previewUiState.update { it.copy(contentVersion = it.contentVersion + 1) }
    }

    internal fun recordConsoleMessage(message: ConsoleMessage) {
        val running = _previewUiState.value.status == PreviewStatus.RUNNING
        if (!running) return
        _previewUiState.update { state ->
            state.copy(consoleMessages = state.consoleMessages + message)
        }
        // 数据层（agent 工具读取）：webkit 模型 → RuntimeLogEntry
        val source = if (message.sourceId().isNotEmpty()) {
            "${message.sourceId()}:${message.lineNumber()}"
        } else {
            ""
        }
        runtimeLogStore.record(
            projectId = projectId ?: return,
            entry = RuntimeLogEntry(
                level = message.messageLevel().name,
                message = message.message(),
                source = source,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /** 刷新/重新加载时调用：清展示面板，但保留 store——旧错误是 agent 下一回合的诊断依据。 */
    internal fun clearConsolePanel() {
        _previewUiState.update { it.copy(consoleMessages = emptyList()) }
    }

    /** 用户显式清空：面板与 store 双清。 */
    fun clearConsoleMessages() {
        _previewUiState.update { it.copy(consoleMessages = emptyList()) }
        projectId?.let(runtimeLogStore::clear)
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
            "http-server" -> Uri.parse(fileServer.start(previewRoot, 0, config.preview.fallback).toString())
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
