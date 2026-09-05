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
import java.util.Date
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

/**
 * 加载失败弹窗数据——只带错误计数、发生时间与出错文件锚点。
 * 详情不展示给用户（控制台可看）、不直接发给 AI（agent 调 read_runtime_logs 自取）。
 */
internal data class PreviewPageError(val count: Int, val atMillis: Long, val file: String)

/** 单次主 frame 加载失败明细（报告表格的一行）。 */
internal data class PageErrorDetail(val atMillis: Long, val label: String, val url: String)

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

    /** 当前导航是否失败过（错误页自身也会走完 started→finished，不能靠回调重置）。 */
    private var navigationFailed = false

    /** 预览根目录相对 workspace 的路径（"" = workspace 根），用于把 URL 映射回出错文件。 */
    private var previewRootInWorkspace = ""

    /** 根路径请求（"/"）实际服务的文件（http-server default / asset-loader entry）。 */
    private var defaultEntryFile = "index.html"

    /** 弹窗周期内的错误明细——确认发送时组装成时间轴表格报告。 */
    private val pendingErrorDetails = mutableListOf<PageErrorDetail>()

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
        // 置位先于弹窗逻辑:弹窗开着时自动 reload 又失败,finished 也不能清掉弹窗。
        navigationFailed = true
        // 详情进 console 面板（用户看）与 store（agent read_runtime_logs 看）——同一条双写。
        recordLogEntry(
            level = "ERROR",
            message = "Preview page failed to load: $description (code $code) at $url",
            source = url
        )
        val now = System.currentTimeMillis()
        pendingErrorDetails += PageErrorDetail(atMillis = now, label = description, url = url)
        val file = fileForUrl(url)
        _previewUiState.update { state ->
            val previous = state.pageError
            if (previous == null) {
                state.copy(pageError = PreviewPageError(count = 1, atMillis = now, file = file))
            } else {
                state.copy(pageError = previous.copy(count = previous.count + 1))
            }
        }
    }

    /**
     * 组装错误报告消息文本（marker 首行 = 折叠摘要，正文 = 时间轴 + err 表格）。
     * 发送给 AI 的这条用户消息自描述——agent 可读表格，长详情仍由 read_runtime_logs 提供。
     */
    fun buildErrorReportText(): String? {
        val error = _previewUiState.value.pageError ?: return null
        val details = pendingErrorDetails.toList()
        if (details.isEmpty()) return null
        val summaryTime = TIME_FORMAT.format(Date(error.atMillis))
        val lines = buildString {
            append("[error-report] Preview load failure — ")
            append("${error.count} error(s) on ${error.file} at $summaryTime\n\n")
            append("| # | Time | Error | File |\n")
            append("|---|------|-------|------|\n")
            details.forEachIndexed { index, detail ->
                append("| ${index + 1} | ${SECOND_FORMAT.format(Date(detail.atMillis))} | ")
                append("${detail.label} | ${fileForUrl(detail.url)} |\n")
            }
        }
        return lines
    }

    /** URL → workspace 相对文件：给 AI 一个锚点，细节仍由 read_runtime_logs 提供。 */
    private fun fileForUrl(url: String): String {
        val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
        val fileName = if (path.isNullOrEmpty() || path == "/") defaultEntryFile else path.removePrefix("/")
        return if (previewRootInWorkspace.isEmpty()) fileName else "$previewRootInWorkspace/$fileName"
    }

    /** 主动发起导航（loadUrl/reload）前调用——重置失败标志。 */
    internal fun onNavigationStarted() {
        navigationFailed = false
    }

    /**
     * 主 frame 加载完成——**无错误的**导航完成才算页面恢复。
     * 错误页（404 等）自身也会走完 started→finished，不能据此清除刚记录的错误。
     */
    internal fun onPageFinished() {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        if (navigationFailed) return
        _previewUiState.update { it.copy(pageError = null) }
        pendingErrorDetails.clear()
    }

    fun dismissPageError() {
        _previewUiState.update { it.copy(pageError = null) }
        pendingErrorDetails.clear()
    }

    /** agent 回合完成——工作区内容可能已变，预览需要按新版本重新加载。 */
    fun onAgentTurnCompleted() {
        _previewUiState.update { it.copy(contentVersion = it.contentVersion + 1) }
    }

    internal fun recordConsoleMessage(message: ConsoleMessage) {
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

    /** console 面板（UI 展示）+ store（agent read_runtime_logs）同一条双写。 */
    private fun recordLogEntry(level: String, message: String, source: String) {
        if (_previewUiState.value.status != PreviewStatus.RUNNING) return
        val levelName = level
        _previewUiState.update { state ->
            state.copy(
                consoleMessages = state.consoleMessages + ConsoleMessage(
                    message,
                    source,
                    0,
                    ConsoleMessage.MessageLevel.valueOf(levelName)
                )
            )
        }
        runtimeLogStore.record(
            projectId = projectId ?: return,
            entry = RuntimeLogEntry(
                level = levelName,
                message = message,
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
        previewRootInWorkspace = config.preview.root
            .trim('/', '\\')
            .let { if (it.isEmpty() || it == ".") "" else it }
        defaultEntryFile = config.preview.fallback ?: config.preview.entry

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
        val TIME_FORMAT = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val SECOND_FORMAT = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    }
}
