package com.aeibi.design.feature.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.ai.AgentEvent
import com.aeibi.design.ai.KoogAgentRunner
import com.aeibi.design.data.sessions.MessageOrigin
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionEntryEntity
import com.aeibi.design.data.sessions.SessionEntryType
import com.aeibi.design.data.sessions.SessionRepository
import com.aeibi.design.data.sessions.TurnStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

sealed interface ChatTimelineItem {
    val id: String

    data class Message(
        override val id: String,
        val role: ChatRole,
        val text: String,
        val status: ChatMessageStatus = ChatMessageStatus.COMPLETE
    ) : ChatTimelineItem

    data class Thinking(override val id: String, val text: String) : ChatTimelineItem

    data class ToolCall(override val id: String, val name: String) : ChatTimelineItem

    data class ToolResult(override val id: String, val name: String, val isError: Boolean) : ChatTimelineItem
}

data class ChatUiState(
    val sessionId: String? = null,
    val input: String = "",
    val timeline: List<ChatTimelineItem> = emptyList(),
    val isLoadingSession: Boolean = false,
    val streamingResponses: List<StreamingResponse> = emptyList(),
    val streamingText: String? = null,
    val streamingStatus: ChatMessageStatus = ChatMessageStatus.WORKING,
    val isRunning: Boolean = false
)

data class StreamingResponse(val id: Int, val thinkingText: String = "", val text: String = "")

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRunner: KoogAgentRunner,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var projectId: String? = null
    private var sessionId: String? = null
    private var entriesJob: Job? = null
    private var runJob: Job? = null
    private var nextStreamingResponseId = 0
    private var persistedAssistantMessageCount = 0

    fun bind(projectId: String, sessionId: String?) {
        if (this.projectId == projectId && this.sessionId == sessionId) return

        runJob?.cancel()
        entriesJob?.cancel()
        this.projectId = projectId
        this.sessionId = sessionId
        nextStreamingResponseId = 0
        persistedAssistantMessageCount = 0
        _uiState.value = ChatUiState(
            sessionId = sessionId,
            isLoadingSession = sessionId != null
        )
        sessionId?.let(::observeEntries)
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun send(onSessionCreated: (String) -> Unit = {}) {
        val input = _uiState.value.input.trim()
        val activeProjectId = projectId ?: return
        if (input.isEmpty() || _uiState.value.isRunning) return

        val activeSessionId = sessionId ?: UUID.randomUUID().toString().also { createdSessionId ->
            sessionId = createdSessionId
            observeEntries(createdSessionId)
            onSessionCreated(createdSessionId)
        }
        _uiState.update {
            it.copy(
                sessionId = activeSessionId,
                input = "",
                streamingResponses = emptyList(),
                streamingText = null,
                streamingStatus = ChatMessageStatus.WORKING,
                isRunning = true
            )
        }

        runJob = viewModelScope.launch {
            var agentStarted = false
            try {
                ensureSession(activeProjectId, activeSessionId, input)
                agentStarted = true
                agentRunner.run(activeProjectId, activeSessionId, input, ::onAgentEvent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!agentStarted) {
                    _uiState.update {
                        it.copy(
                            streamingText = error.message ?: error.javaClass.simpleName,
                            streamingStatus = ChatMessageStatus.FAILED
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isRunning = false) }
                if (runJob == coroutineContext.job) runJob = null
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
    }

    private fun observeEntries(sessionId: String) {
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            sessionRepository.observeEntries(sessionId).collect { entries ->
                if (this@ChatViewModel.sessionId != sessionId) return@collect
                val timeline = entries.toTimeline(sessionRepository)
                val assistantMessageCount = entries.count { entry ->
                    entry.type == SessionEntryType.MESSAGE.name &&
                        sessionRepository.decodeMessage(entry).origin == MessageOrigin.ASSISTANT
                }
                val newAssistantMessages = (assistantMessageCount - persistedAssistantMessageCount).coerceAtLeast(0)
                persistedAssistantMessageCount = assistantMessageCount
                val turnFinished = entries.lastOrNull()?.type == SessionEntryType.TURN_FINISHED.name
                _uiState.update { state ->
                    state.copy(
                        timeline = timeline,
                        isLoadingSession = false,
                        streamingResponses = if (turnFinished) {
                            emptyList()
                        } else {
                            state.streamingResponses.drop(newAssistantMessages)
                        },
                        streamingText = if (turnFinished) null else state.streamingText
                    )
                }
            }
        }
    }

    private fun onAgentEvent(event: AgentEvent) {
        _uiState.update { state ->
            when (event) {
                AgentEvent.ResponseStarted -> state.copy(
                    streamingResponses = state.streamingResponses + StreamingResponse(nextStreamingResponseId++)
                )
                is AgentEvent.TextDelta -> state.updateStreamingResponse { response ->
                    response.copy(text = response.text + event.text)
                }
                is AgentEvent.ReasoningDelta -> state.updateStreamingResponse { response ->
                    response.copy(thinkingText = response.thinkingText + event.text)
                }
                is AgentEvent.ToolStarted,
                is AgentEvent.ToolFinished -> state
            }
        }
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
}

private fun ChatUiState.updateStreamingResponse(transform: (StreamingResponse) -> StreamingResponse): ChatUiState {
    val response = streamingResponses.lastOrNull() ?: return this
    return copy(streamingResponses = streamingResponses.dropLast(1) + transform(response))
}

internal fun List<SessionEntryEntity>.toTimeline(repository: SessionRepository): List<ChatTimelineItem> {
    val timeline = mutableListOf<ChatTimelineItem>()
    forEach { entry ->
        when (entry.type) {
            SessionEntryType.MESSAGE.name -> {
                val payload = repository.decodeMessage(entry)
                when (val message = payload.message) {
                    is Message.User -> when (payload.origin) {
                        MessageOrigin.USER -> timeline += ChatTimelineItem.Message(
                            id = entry.id.toString(),
                            role = ChatRole.USER,
                            text = message.textContent()
                        )
                        MessageOrigin.TOOL -> {
                            message.parts
                                .filterIsInstance<MessagePart.Tool.Result>()
                                .forEachIndexed { index, result ->
                                    timeline += ChatTimelineItem.ToolResult(
                                        id = "${entry.id}:tool-result:$index",
                                        name = result.tool,
                                        isError = result.isError
                                    )
                                }
                        }
                        MessageOrigin.ASSISTANT -> Unit
                    }
                    is Message.Assistant -> {
                        message.parts.forEachIndexed { index, part ->
                            when (part) {
                                is MessagePart.Text -> part.text.takeIf(String::isNotBlank)?.let { text ->
                                    timeline += ChatTimelineItem.Message(
                                        id = "${entry.id}:text:$index",
                                        role = ChatRole.ASSISTANT,
                                        text = text
                                    )
                                }
                                is MessagePart.Reasoning -> {
                                    val text = part.content.ifEmpty { part.summary.orEmpty() }
                                        .joinToString("\n")
                                    text.takeIf(String::isNotBlank)?.let {
                                        timeline += ChatTimelineItem.Thinking(
                                            id = "${entry.id}:thinking:$index",
                                            text = it
                                        )
                                    }
                                }
                                is MessagePart.Tool.Call -> timeline += ChatTimelineItem.ToolCall(
                                    id = part.id ?: "${entry.id}:$index",
                                    name = part.tool
                                )
                                else -> Unit
                            }
                        }
                    }
                    is Message.System -> Unit
                }
            }
            SessionEntryType.TURN_FINISHED.name -> {
                val payload = repository.decodeTurnFinished(entry)
                val partialReasoning = payload.partialReasoning?.takeIf(String::isNotBlank)
                val partialResponse = payload.partialResponse?.takeIf(String::isNotBlank)
                partialReasoning?.let { text ->
                    timeline += ChatTimelineItem.Thinking(
                        id = "${entry.id}:partial-thinking",
                        text = text
                    )
                }
                partialResponse?.let { text ->
                    timeline += ChatTimelineItem.Message(
                        id = "${entry.id}:partial",
                        role = ChatRole.ASSISTANT,
                        text = text,
                        status = if (payload.status == TurnStatus.CANCELLED) {
                            ChatMessageStatus.CANCELLED
                        } else {
                            ChatMessageStatus.COMPLETE
                        }
                    )
                }
                when (payload.status) {
                    TurnStatus.FAILED -> timeline += ChatTimelineItem.Message(
                        id = entry.id.toString(),
                        role = ChatRole.ASSISTANT,
                        text = payload.failure?.message.orEmpty(),
                        status = ChatMessageStatus.FAILED
                    )
                    TurnStatus.CANCELLED -> if (partialResponse == null) {
                        timeline += ChatTimelineItem.Message(
                            id = entry.id.toString(),
                            role = ChatRole.ASSISTANT,
                            text = "",
                            status = ChatMessageStatus.CANCELLED
                        )
                    }
                    TurnStatus.COMPLETE -> Unit
                }
            }
            SessionEntryType.CONTEXT_REPLACED.name -> Unit
        }
    }
    return timeline
}
