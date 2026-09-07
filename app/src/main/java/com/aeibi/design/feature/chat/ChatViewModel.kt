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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    CANCELLED,
    INCOMPLETE
}

sealed interface ChatTimelineItem {
    val id: String

    data class Message(
        override val id: String,
        val role: ChatRole,
        val text: String,
        val status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
        /** 流式消息的本次增量（自上次 item 之后新增的文本）——markdown streaming append 用。 */
        val textDelta: String? = null
    ) : ChatTimelineItem

    data class Thinking(override val id: String, val text: String, val isStreaming: Boolean = false) : ChatTimelineItem

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
    val isRunning: Boolean = false,
    /** 待发送附件（输入框上方折叠条），发送时并入消息正文。 */
    val attachment: PendingAttachment? = null
)

data class StreamingResponse(
    val id: Int,
    val thinkingText: String = "",
    val text: String = "",
    /** 最近一次 TextDelta 增量——供流式 markdown append（text 保留作状态恢复）。 */
    val lastDelta: String = ""
)

/**
 * 待发送附件——输入框上方可折叠的引用条（运行日志/未来的文件/截图引用都走这里）。
 * 发送时随用户输入一起作为消息正文发出。
 */
data class PendingAttachment(val title: String, val body: String)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRunner: KoogAgentRunner,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())

    /** agent 回合内请求刷新预览（reload_preview 工具）——UI 层先清日志再执行 reload。 */
    private val _previewReloadRequested =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val previewReloadRequested: SharedFlow<Unit> = _previewReloadRequested.asSharedFlow()
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

    /** 添加待发送附件（首行作折叠标题，其余作正文）——「添加到聊天」入口。 */
    fun attachDraft(text: String) {
        val lines = text.lineSequence().map(String::trimEnd).toList()
        val title = lines.firstOrNull()?.takeIf(String::isNotBlank) ?: return
        val body = lines.drop(1).filter(String::isNotBlank).joinToString("\n")
        _uiState.update { it.copy(attachment = PendingAttachment(title = title, body = body)) }
    }

    fun removeAttachment() {
        _uiState.update { it.copy(attachment = null) }
    }

    fun send(onSessionCreated: (String) -> Unit = {}) {
        val state = _uiState.value
        val attachment = state.attachment
        val input = state.input.trim()
        val activeProjectId = projectId ?: return
        if (input.isEmpty() && attachment == null) return
        if (state.isRunning) return
        // 附件正文 + 用户补充文字一起作为消息发出；发送后附件清除。
        val message = listOfNotNull(
            attachment?.let { "${it.title}\n${it.body}" },
            input
        ).joinToString("\n\n")

        val activeSessionId = sessionId ?: UUID.randomUUID().toString().also { createdSessionId ->
            sessionId = createdSessionId
            observeEntries(createdSessionId)
            onSessionCreated(createdSessionId)
        }
        _uiState.update {
            it.copy(
                sessionId = activeSessionId,
                input = "",
                attachment = null,
                streamingResponses = emptyList(),
                streamingText = null,
                streamingStatus = ChatMessageStatus.WORKING,
                isRunning = true
            )
        }

        runJob = viewModelScope.launch {
            var agentStarted = false
            try {
                ensureSession(activeProjectId, activeSessionId, message)
                agentStarted = true
                // 临时诊断入口（PR 前删除）：#mdtest = token-free 本地合成流式 markdown
                // 回复——复现真实 LLM 的增量节奏（闭合块解析/迟到生长），测试滚动跟随。
                if (message.trim() == MD_TEST_COMMAND || message.trim() == MD_TEST_COMMAND_PLAIN) {
                    fakeMarkdownStream(plain = message.trim() == MD_TEST_COMMAND_PLAIN)
                } else {
                    agentRunner.run(activeProjectId, activeSessionId, message, ::onAgentEvent)
                }
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

    /**
     * 临时诊断流（PR 前删除）：本地合成一段 markdown 回复，按真实 LLM 的增量节奏
     * 逐 chunk 发 TextDelta——闭合块（代码/表格/列表）完成后流式渲染器会做一次
     * 异步解析 → 布局生长迟到于文本到达，正是滚动竞态的复现条件。不触网、不耗 token。
     */
    private suspend fun fakeMarkdownStream(plain: Boolean = false) {
        onAgentEvent(AgentEvent.ResponseStarted)
        try {
            // 简短 reasoning 前缀（同步渲染 Thinking 流式条目）。
            for (chunk in "（本地诊断流：以下为合成 markdown，无 token 消耗）".chunked(4)) {
                delay(14)
                onAgentEvent(AgentEvent.ReasoningDelta(chunk))
            }
            if (plain) {
                // 纯文本对照流：无任何 markdown 语法——渲染树恒定，只有文本行增长。
                val doc = PLAIN_TEST_DOC
                var i = 0
                while (i < doc.length) {
                    val size = kotlin.random.Random.nextInt(4, 18)
                    val end = minOf(i + size, doc.length)
                    val chunk = doc.substring(i, end)
                    i = end
                    onAgentEvent(AgentEvent.TextDelta(chunk))
                    delay(if (chunk.contains('\n')) kotlin.random.Random.nextLong(60L, 180L) else 14L)
                }
                return
            }
            val doc = FAKE_MARKDOWN_DOC
            var i = 0
            while (i < doc.length) {
                val size = kotlin.random.Random.nextInt(4, 18)
                val end = minOf(i + size, doc.length)
                val chunk = doc.substring(i, end)
                i = end
                // 越行边界处给一帧停顿，让段落/块按真实节奏闭合。
                val crossesLine = chunk.contains('\n')
                onAgentEvent(AgentEvent.TextDelta(chunk))
                if (crossesLine) {
                    delay(kotlin.random.Random.nextLong(80L, 260L))
                } else {
                    delay(16L)
                }
            }
            // 流结束：模拟真实回复的最后一块文本落定（渲染收敛后再停）。
            delay(400L)
        } catch (error: kotlinx.coroutines.CancellationException) {
            // 用户取消：清掉半截虚拟流，避免幽灵气泡残留。
            _uiState.update {
                it.copy(streamingResponses = emptyList(), streamingText = null)
            }
            throw error
        }
    }

    private companion object {
        /** 临时诊断命令（PR 前删除）。 */
        const val MD_TEST_COMMAND = "#mdtest"

        /** 临时纯文本对照流（PR 前删除）——无 markdown 语法。 */
        const val MD_TEST_COMMAND_PLAIN = "#mdtest-plain"

        /** 纯文本对照文档：长段落，无任何 md 结构。 */
        const val PLAIN_TEST_DOC = """纯文本对照回复。这一整段没有任何 markdown 语法，只有连续的中文句子被逐字流式到达。用于区分 preview 往返的逐行下落是 markdown 渲染树特有的问题，还是所有文本行共有的重排问题。

第二段继续纯文本。如果这段文字在 preview 往返时也逐行下落，说明问题在文本行布局与隐藏-恢复机制的交互；如果只有带 markdown 结构的回复才下落，说明问题在 markdown 渲染器内部。这里补足行数，让内容足够长以便观察多行。这一段文字会继续写下去，直到长度足够撑出十几行文本，这样切 preview 再回来时，行与行之间的落位差异会很明显。

第三段。收尾。"""

        /** 合成 markdown：多闭合块 + 混合结构，制造足够多的异步解析生长点。 */
        const val FAKE_MARKDOWN_DOC = """# 合成回复诊断文档

这是**本地合成**的一段 markdown 回复，用于在没有 LLM token 消耗的情况下复现流式渲染与滚动跟随的手势竞态。

## 流式特性说明

真实回复流式到达时，只有**已闭合的块**会被完整渲染：

- 段落遇到空行才闭合
- 代码块遇到结束反引号才闭合
- 表格的行逐步追加

所以闭合块解析完成的时刻**滞后**于文本到达——这就是布局生长迟到、与用户手势竞争的来源。

## 一个 Kotlin 代码块

下面这段代码会在反引号闭合时做一次完整解析：

```kotlin
fun main() {
    val messages = listOf(
        "上划读历史应断开跟随",
        "下滑触底才恢复跟随",
        "按住期间不自动滚动"
    )
    messages.forEachIndexed { index, text ->
        println("#${'$'}{index + 1} ${'$'}text")
        Thread.sleep(50L)
    }
}
```

### 列表与嵌套

1. 第一层：流式增量
   - 增量走事件层 append
   - 全量文本保留用于状态恢复
2. 第二层：收敛滚动
   - 内容高度每增长一次滚一次
   - 解析再撑高会再次触发

> 引用块：竞态只在「跟随还开着 + 生长事件在用户手势之后到达」时发生。

## 表格演示

| 手势 | 开关 | 说明 |
|---|---|---|
| 上划（读历史） | 断开 | 第一帧即断，无范围容差 |
| 按住 | 断开 | 拖动停顿超过阈值 |
| 下滑触底 | 跟随 | 瞬时碰到底即通 |

## 第二个代码块（JSON）

```json
{
  "scroll": {
    "follow": true,
    "reason": "底部姿态",
    "locked": false
  },
  "gesture": "drag-up",
  "frames": [1, 2, 3, 4, 5, 6, 7, 8]
}
```

## 收尾说明

- 诊断入口命令为 `#mdtest`，发送即触发
- 回复结束后条目保留为工作态，再次发送会清掉重来
- 该入口随 PR 清理移除，不进入正式代码

合成结束。如果这段内容渲染平滑、三个手势点都正常，竞态就修好了。"""
    }

    private fun observeEntries(sessionId: String) {
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            android.util.Log.d("ChatDebug", "observeEntries start recover session=$sessionId")
            sessionRepository.recoverInterruptedSession(sessionId)
            android.util.Log.d("ChatDebug", "observeEntries recover done, collecting session=$sessionId")
            sessionRepository.observeEntries(sessionId).collect { entries ->
                if (this@ChatViewModel.sessionId != sessionId) return@collect
                android.util.Log.d("ChatDebug", "observeEntries emit count=${entries.size} session=$sessionId")
                val timeline = entries.toTimeline(sessionRepository)
                android.util.Log.d("ChatDebug", "observeEntries timeline=${timeline.size}")
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
                    response.copy(text = response.text + event.text, lastDelta = event.text)
                }
                is AgentEvent.ReasoningDelta -> state.updateStreamingResponse { response ->
                    response.copy(thinkingText = response.thinkingText + event.text)
                }
                is AgentEvent.ToolStarted,
                is AgentEvent.ToolFinished -> state
                AgentEvent.PreviewReloadRequested -> {
                    _previewReloadRequested.tryEmit(Unit)
                    state
                }
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
                        status = when (payload.status) {
                            TurnStatus.CANCELLED -> ChatMessageStatus.CANCELLED
                            TurnStatus.INCOMPLETE -> ChatMessageStatus.INCOMPLETE
                            else -> ChatMessageStatus.COMPLETE
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
                    TurnStatus.CANCELLED,
                    TurnStatus.INCOMPLETE -> if (partialResponse == null) {
                        timeline += ChatTimelineItem.Message(
                            id = entry.id.toString(),
                            role = ChatRole.ASSISTANT,
                            text = "",
                            status = if (payload.status == TurnStatus.CANCELLED) {
                                ChatMessageStatus.CANCELLED
                            } else {
                                ChatMessageStatus.INCOMPLETE
                            }
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
