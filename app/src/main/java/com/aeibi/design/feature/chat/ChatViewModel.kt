package com.aeibi.design.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.ai.chat.AiChatService
import com.aeibi.design.ai.chat.ChatMessage
import com.aeibi.design.ai.chat.ChatRequest
import com.aeibi.design.ai.chat.ResolvedProvider
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.data.messages.MessageEntryType
import com.aeibi.design.data.messages.MessagePayload
import com.aeibi.design.data.messages.MessageRepository
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 聊天状态入口：跟随当前会话展示消息流，重开会话可完整恢复历史消息。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val aiChatService: AiChatService
) : ViewModel() {

    private val currentSessionId = MutableStateFlow<String?>(null)

    private val _streamingTexts = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * 生成中的实时文本:entryId → 目前收到的增量聚合。只存在于内存,
     * 流结束后一次性收敛入库(UI 先看到完整内容,库不承受逐块写放大)。
     */
    val streamingTexts: StateFlow<Map<String, String>> = _streamingTexts.asStateFlow()

    /** 当前会话的消息列表；sessionId 变化时自动切换到新会话的数据源。 */
    val messages: StateFlow<List<MessageEntry>> = currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                messageRepository.observeMessages(sessionId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 绑定当前会话；null 表示尚未选中会话。 */
    fun bind(sessionId: String?) {
        currentSessionId.value = sessionId
    }

    /**
     * 发送消息:用户消息落库 → 追加 ASSISTANT/STREAMING 条目 → 流式请求 →
     * 增量在内存聚合([streamingTexts] 供 UI 实时渲染)→ 流结束一次性收敛
     * COMPLETED(失败则 FAILED)。resolve 失败(无绑定/无 key)直接 FAILED 落库,
     * 不发起网络。
     */
    fun sendMessage(content: String) {
        val sessionId = currentSessionId.value ?: return
        viewModelScope.launch {
            messageRepository.appendMessage(
                sessionId = sessionId,
                type = MessageEntryType.USER_MESSAGE,
                payload = MessagePayload(
                    role = MessageRole.USER,
                    status = MessageStatus.COMPLETED,
                    content = content
                ),
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )

            val provider = resolveProvider(sessionId)
            if (provider == null) {
                appendFailedEntry(sessionId, ERROR_NO_PROVIDER)
                return@launch
            }

            val history = messageRepository.getMessages(sessionId)
            val entry = appendStreamingEntry(sessionId, provider)
            try {
                val aggregated = StringBuilder()
                aiChatService.chatStream(
                    ChatRequest(model = provider.model, messages = buildContext(history)),
                    provider
                ).collect { chunk ->
                    aggregated.append(chunk.delta)
                    _streamingTexts.value = _streamingTexts.value + (entry.id to aggregated.toString())
                }
                completeEntry(entry.id, aggregated.toString())
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                failEntry(entry.id, e.message ?: e.javaClass.simpleName)
            } finally {
                _streamingTexts.value = _streamingTexts.value - entry.id
            }
        }
    }

    /** 解析当前会话绑定的 provider 配置与 key;未绑定/配置已删/无 key 时返回 null。 */
    private suspend fun resolveProvider(sessionId: String): ResolvedProvider? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val configId = session.providerConfigId ?: return null
        val model = session.model ?: return null
        val settings = aiProviderRepository.settings.firstOrNull() ?: return null
        val config = settings.providers.firstOrNull { it.id == configId } ?: return null
        val apiKey = aiProviderRepository.readApiKey(configId) ?: return null
        return ResolvedProvider(
            configId = config.id,
            providerType = config.providerType,
            displayName = config.displayName,
            endpoint = config.endpoint,
            apiKey = apiKey,
            model = model
        )
    }

    /**
     * 组装发给模型的上下文:user 全进,assistant 只进 COMPLETED(中断/失败的回复是
     * UI 事实而非模型事实);再取最近 [CONTEXT_WINDOW] 条控制请求规模。
     */
    internal fun buildContext(history: List<MessageEntry>): List<ChatMessage> = history
        .filter { entry ->
            entry.payload.role == MessageRole.USER || entry.payload.status == MessageStatus.COMPLETED
        }
        .takeLast(CONTEXT_WINDOW)
        .map { entry ->
            ChatMessage(
                role = if (entry.payload.role == MessageRole.USER) {
                    ChatMessage.ROLE_USER
                } else {
                    ChatMessage.ROLE_ASSISTANT
                },
                content = entry.payload.content
            )
        }

    private suspend fun appendStreamingEntry(sessionId: String, provider: ResolvedProvider): MessageEntry =
        messageRepository.appendMessage(
            sessionId = sessionId,
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = MessagePayload(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.STREAMING,
                content = "",
                providerConfigId = provider.configId,
                model = provider.model
            ),
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )

    private suspend fun appendFailedEntry(sessionId: String, error: String) {
        messageRepository.appendMessage(
            sessionId = sessionId,
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = MessagePayload(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.FAILED,
                content = "",
                error = error
            ),
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )
    }

    private suspend fun completeEntry(entryId: String, content: String) {
        messageRepository.updatePayloadAndStatus(
            entryId = entryId,
            content = content,
            newStatus = MessageStatus.COMPLETED,
            allowedFrom = listOf(MessageStatus.STREAMING)
        )
    }

    private suspend fun failEntry(entryId: String, error: String) {
        messageRepository.updatePayloadAndStatus(
            entryId = entryId,
            content = "",
            newStatus = MessageStatus.FAILED,
            allowedFrom = listOf(MessageStatus.STREAMING),
            error = error
        )
    }

    companion object {
        const val CONTEXT_WINDOW = 20

        /**
         * resolve 失败(未绑定 provider/配置已删/无 key)的统一错误码,
         * UI 层据此映射本地化文案;其他 error 值为面向诊断的原始信息。
         */
        const val ERROR_NO_PROVIDER = "no_provider"
    }
}
