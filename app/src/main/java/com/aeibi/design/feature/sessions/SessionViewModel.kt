package com.aeibi.design.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 会话列表状态入口：暴露按项目隔离的会话流，并承载创建、重命名、删除动作。 */
@HiltViewModel
class SessionViewModel @Inject constructor(private val repository: SessionRepository) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private var observeJob: Job? = null

    /** 观察指定项目的会话列表——幂等：重复调用会替换旧观察而非叠加。 */
    fun observe(projectId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeSessions(projectId).collect { _sessions.value = it }
        }
    }

    suspend fun renameSession(sessionId: String, title: String) {
        repository.renameSession(sessionId, title, System.currentTimeMillis())
    }

    /**
     * 「新建会话」：复用项目内已有的空会话（无消息），没有才创建——
     * 多次点击只保留一个空会话；该空会话发出消息后，下次点击才创建新的。
     */
    suspend fun openOrCreateEmptySession(projectId: String): String {
        repository.findEmptySession(projectId)?.let { return it.id }
        return repository.createEmptySession(projectId).id
    }

    suspend fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
    }
}
