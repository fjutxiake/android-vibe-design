package com.aeibi.design.feature.workspace

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.ProjectRepository
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
    val errorMessage: String? = null
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
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        projectRepository: ProjectRepository,
        @ApplicationContext context: Context
    ) : this(
        projectRepository,
        LocalStaticFileServer(),
        LocalStaticAssetLoader(context),
        Dispatchers.IO
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val _previewUiState = MutableStateFlow(PreviewUiState())
    internal val previewUiState: StateFlow<PreviewUiState> = _previewUiState.asStateFlow()

    fun startPreview(projectId: String) {
        if (_previewUiState.value.status !in listOf(PreviewStatus.STOPPED, PreviewStatus.FAILED)) return
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
