package com.aeibi.design.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.ai.AgentEvent
import com.aeibi.design.ai.KoogAgentRunner
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

enum class ChatRole {
    USER,
    ASSISTANT
}

enum class ChatMessageStatus {
    COMPLETE,
    WORKING,
    FAILED,
    CANCELLED
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETE
)

sealed interface ChatTimelineItem {
    val id: String

    data class Message(val message: ChatMessage) : ChatTimelineItem {
        override val id: String = message.id
    }

    data class ToolEvent(
        override val id: String = UUID.randomUUID().toString(),
        val event: AgentEvent
    ) : ChatTimelineItem
}

data class ChatUiState(
    val sessionId: String? = null,
    val input: String = "",
    val timeline: List<ChatTimelineItem> = emptyList(),
    val isRunning: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRunner: KoogAgentRunner,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val timelineByConversation = mutableMapOf<String, List<ChatTimelineItem>>()
    private var projectId: String? = null
    private var sessionId: String? = null
    private var conversationKey: String? = null
    private var runJob: Job? = null

    fun bind(projectId: String, sessionId: String?) {
        val nextKey = conversationKey(projectId, sessionId)
        if (this.projectId == projectId && (this.sessionId == sessionId || conversationKey == nextKey)) return

        conversationKey?.let { timelineByConversation[it] = _uiState.value.timeline }
        runJob?.cancel()
        this.projectId = projectId
        this.sessionId = sessionId
        conversationKey = nextKey
        _uiState.value = ChatUiState(
            sessionId = sessionId,
            timeline = timelineByConversation[nextKey].orEmpty()
        )
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun send() {
        val input = _uiState.value.input.trim()
        val activeProjectId = projectId ?: return
        if (input.isEmpty() || _uiState.value.isRunning) return

        val activeSessionId = sessionId ?: UUID.randomUUID().toString().also { createdSessionId ->
            val oldKey = conversationKey
            sessionId = createdSessionId
            conversationKey = conversationKey(activeProjectId, createdSessionId)
            oldKey?.let(timelineByConversation::remove)
        }
        val assistantId = UUID.randomUUID().toString()
        val assistantMessageIds = mutableListOf(assistantId)
        var activeAssistantMessageId: String? = assistantId
        _uiState.update { state ->
            state.copy(
                sessionId = activeSessionId,
                input = "",
                timeline = state.timeline + listOf(
                    ChatTimelineItem.Message(ChatMessage(role = ChatRole.USER, text = input)),
                    ChatTimelineItem.Message(
                        ChatMessage(
                            id = assistantId,
                            role = ChatRole.ASSISTANT,
                            text = "",
                            status = ChatMessageStatus.WORKING
                        )
                    )
                ),
                isRunning = true
            )
        }
        val runKey = requireNotNull(conversationKey)
        timelineByConversation[runKey] = _uiState.value.timeline

        runJob = viewModelScope.launch {
            try {
                ensureSession(activeProjectId, activeSessionId, input)
                val response = agentRunner.run(activeProjectId, activeSessionId, input) { event ->
                    activeAssistantMessageId = onAgentEvent(
                        key = runKey,
                        assistantId = activeAssistantMessageId,
                        assistantMessageIds = assistantMessageIds,
                        event = event
                    )
                }
                updateAssistant(
                    key = runKey,
                    assistantMessageIds = assistantMessageIds,
                    activeAssistantMessageId = activeAssistantMessageId,
                    text = response,
                    status = ChatMessageStatus.COMPLETE
                )
                sessionRepository.touchSession(activeSessionId, System.currentTimeMillis())
            } catch (error: CancellationException) {
                updateAssistant(
                    key = runKey,
                    assistantMessageIds = assistantMessageIds,
                    activeAssistantMessageId = activeAssistantMessageId,
                    text = null,
                    status = ChatMessageStatus.CANCELLED
                )
                throw error
            } catch (error: Exception) {
                updateAssistant(
                    key = runKey,
                    assistantMessageIds = assistantMessageIds,
                    activeAssistantMessageId = activeAssistantMessageId,
                    text = error.message ?: error.javaClass.simpleName,
                    status = ChatMessageStatus.FAILED
                )
            } finally {
                if (conversationKey == runKey) {
                    _uiState.update { it.copy(isRunning = false) }
                    timelineByConversation[runKey] = _uiState.value.timeline
                }
                if (runJob == coroutineContext.job) runJob = null
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
    }

    private suspend fun ensureSession(projectId: String, sessionId: String, firstMessage: String) {
        if (sessionRepository.getSession(sessionId) != null) return
        val now = System.currentTimeMillis()
        sessionRepository.saveSession(
            SessionEntity(
                id = sessionId,
                projectId = projectId,
                title = firstMessage.take(48),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun onAgentEvent(
        key: String,
        assistantId: String?,
        assistantMessageIds: MutableList<String>,
        event: AgentEvent
    ): String? {
        if (conversationKey != key) return assistantId
        return when (event) {
            is AgentEvent.TextDelta -> {
                appendAssistantText(key, assistantId, event.text).also { messageId ->
                    if (messageId !in assistantMessageIds) assistantMessageIds += messageId
                }
            }
            is AgentEvent.ToolStarted,
            is AgentEvent.ToolFinished -> {
                _uiState.update { state ->
                    state.copy(timeline = state.timeline + ChatTimelineItem.ToolEvent(event = event))
                }
                timelineByConversation[key] = _uiState.value.timeline
                null
            }
        }
    }

    private fun appendAssistantText(key: String, id: String?, delta: String): String {
        val messageId = id ?: UUID.randomUUID().toString()
        _uiState.update { state ->
            state.copy(
                timeline = if (id == null) {
                    state.timeline + ChatTimelineItem.Message(
                        ChatMessage(
                            id = messageId,
                            role = ChatRole.ASSISTANT,
                            text = delta,
                            status = ChatMessageStatus.WORKING
                        )
                    )
                } else {
                    state.timeline.map { item ->
                        if (item is ChatTimelineItem.Message && item.message.id == messageId) {
                            item.copy(message = item.message.copy(text = item.message.text + delta))
                        } else {
                            item
                        }
                    }
                }
            )
        }
        timelineByConversation[key] = _uiState.value.timeline
        return messageId
    }

    private fun updateAssistant(
        key: String,
        assistantMessageIds: List<String>,
        activeAssistantMessageId: String?,
        text: String?,
        status: ChatMessageStatus
    ) {
        val appendFinalMessage = activeAssistantMessageId == null && !text.isNullOrBlank()
        val finalMessageId = activeAssistantMessageId ?: assistantMessageIds.last()

        fun update(timeline: List<ChatTimelineItem>): List<ChatTimelineItem> = buildList {
            timeline.forEach { item ->
                if (item is ChatTimelineItem.Message && item.message.id in assistantMessageIds) {
                    val updatedText = when {
                        item.message.id != finalMessageId -> item.message.text
                        status != ChatMessageStatus.COMPLETE && text != null -> text
                        item.message.text.isBlank() && text != null -> text
                        else -> item.message.text
                    }
                    add(item.copy(message = item.message.copy(text = updatedText, status = status)))
                } else {
                    add(item)
                }
            }
            if (appendFinalMessage) {
                add(
                    ChatTimelineItem.Message(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = requireNotNull(text),
                            status = status
                        )
                    )
                )
            }
        }

        if (conversationKey == key) {
            _uiState.update { state -> state.copy(timeline = update(state.timeline)) }
            timelineByConversation[key] = _uiState.value.timeline
        } else {
            timelineByConversation[key] = update(timelineByConversation[key].orEmpty())
        }
    }

    private fun conversationKey(projectId: String, sessionId: String?): String =
        "$projectId:${sessionId ?: NEW_SESSION_KEY}"

    private companion object {
        const val NEW_SESSION_KEY = "new"
    }
}
