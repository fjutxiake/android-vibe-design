package com.aeibi.design.feature.versions

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.versions.VersionSnapshot
import com.aeibi.design.data.versions.VersionSnapshotService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 版本页状态：列表数据来自 [VersionSnapshotService]。 */
@HiltViewModel
class ProjectVersionsViewModel internal constructor(
    private val projectRepository: ProjectRepository,
    private val versionSnapshotService: VersionSnapshotService,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        projectRepository: ProjectRepository,
        versionSnapshotService: VersionSnapshotService,
        @ApplicationContext context: Context
    ) : this(projectRepository, versionSnapshotService, Dispatchers.IO)

    data class UiState(
        val loading: Boolean = true,
        val ready: Boolean = false,
        val versions: List<VersionSnapshot> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var projectId: String? = null

    fun load(projectId: String) {
        this.projectId = projectId
        viewModelScope.launch { reload() }
    }

    fun createSnapshot(label: String) = operate {
        versionSnapshotService.createSnapshot(requireProjectId(), label)
    }

    fun restore(snapshotId: String, label: String) = operate {
        versionSnapshotService.restore(requireProjectId(), snapshotId, label)
    }

    private fun operate(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { action() }.fold(onSuccess = { reload() }, onFailure = ::postError)
        }
    }

    private suspend fun reload() {
        val id = projectId ?: return
        runCatching {
            withContext(ioDispatcher) {
                val project = projectRepository.getProject(id)
                if (project?.isInitialized != true) return@withContext null
                var versions = versionSnapshotService.snapshots(id)
                if (versions.isEmpty()) {
                    // 初始化时的 INIT 快照若失败,进入版本页时自愈补建;再失败则以
                    // 空列表 + 错误信息呈现,不再是静默无保护状态。
                    runCatching { versionSnapshotService.ensureInitialSnapshot(id) }
                        .onFailure { Log.w(TAG, "INIT 快照补建失败", it) }
                    versions = versionSnapshotService.snapshots(id)
                }
                versions
            }
        }.fold(
            onSuccess = { versions ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        ready = versions != null,
                        versions = versions.orEmpty(),
                        busy = false
                    )
                }
            },
            onFailure = ::postError
        )
    }

    private fun postError(error: Throwable) {
        _uiState.update {
            it.copy(loading = false, busy = false, message = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun requireProjectId(): String = checkNotNull(projectId) { "项目尚未加载" }

    private companion object {
        const val TAG = "ProjectVersionsVM"
    }
}
