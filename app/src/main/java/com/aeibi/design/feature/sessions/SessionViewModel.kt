package com.aeibi.design.feature.sessions

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.R
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 会话列表状态入口：暴露按项目隔离的会话流，并承载创建、重命名、删除动作。 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    /** 开始观察指定项目的会话列表，Room 数据变化会自动推送到 [sessions]。 */
    fun observe(projectId: String) {
        viewModelScope.launch {
            repository.observeSessions(projectId).collect { _sessions.value = it }
        }
    }

    /** 创建新会话并返回其 id，调用方可直接导航进入。 */
    suspend fun createSession(projectId: String): String {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = defaultTitle(),
            createdAt = now,
            updatedAt = now
        )
        repository.saveSession(session)
        return session.id
    }

    suspend fun renameSession(sessionId: String, title: String) {
        repository.renameSession(sessionId, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
    }

    private fun defaultTitle(): String = ContextCompat.getString(context, R.string.session_default_title)
}
