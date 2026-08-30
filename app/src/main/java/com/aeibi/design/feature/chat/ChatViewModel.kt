package com.aeibi.design.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.R
import com.aeibi.design.ai.chat.AiChatProtocolException
import com.aeibi.design.ai.chat.AiChatService
import com.aeibi.design.ai.chat.ChatMessage
import com.aeibi.design.ai.chat.ChatRequest
import com.aeibi.design.ai.chat.ResolvedProvider
import com.aeibi.design.ai.provider.AiProviderRegistry
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.ai.DefaultProviderSelection
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.data.messages.MessageEntryType
import com.aeibi.design.data.messages.MessagePayload
import com.aeibi.design.data.messages.MessageRepository
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 会话级选择面板中的一个可选项:配置 + 图标 + 是否已存 key(缺 key 时禁选)。 */
data class SessionProviderOption(val config: ProviderConfig, val iconRes: Int, val hasApiKey: Boolean)

/** 一次生效的 provider/model 选择,供顶栏与面板展示当前值。 */
data class SessionProviderSelection(
    val providerConfigId: String,
    val displayName: String,
    val iconRes: Int,
    val model: String
)

/** 会话级 provider 状态:[current] 是下一条消息将实际使用的选择。 */
data class SessionProviderUiState(
    val current: SessionProviderSelection? = null,
    /** 会话未绑定(跟随全局默认)时为 true。 */
    val followsDefault: Boolean = true,
    val defaultSelection: SessionProviderSelection? = null,
    val options: List<SessionProviderOption> = emptyList()
)

/** 聊天状态入口：跟随当前会话展示消息流，重开会话可完整恢复历史消息。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val aiChatService: AiChatService,
    private val providerRegistry: AiProviderRegistry
) : ViewModel() {

    private val currentSessionId = MutableStateFlow<String?>(null)

    private val _isGenerating = MutableStateFlow(false)
    private var activeGeneration: Job? = null

    /** 是否有回复正在生成;生成中输入区禁用并展示停止按钮。 */
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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
     * 会话级 provider 状态(顶栏展示 + 选择面板数据)。派生规则与
     * [resolveProvider] 一致:会话绑定优先,绑定失效/未绑定时当前值取全局
     * 默认 —— 展示的就是下一条消息实际会用的选择。
     */
    val sessionProvider: StateFlow<SessionProviderUiState> = currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(null)
            } else {
                sessionRepository.observeSession(sessionId)
            }
        }
        .combine(aiProviderRepository.settings) { session, settings -> session to settings.providers }
        .combine(aiProviderRepository.defaultSelection) { (session, providers), default ->
            deriveSessionProviderUi(session, providers, default)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionProviderUiState())

    /**
     * 选择本会话使用的 provider/model(null/null 解除绑定,回到跟随全局默认)。
     * 只影响后续消息;进行中的生成沿用发起时已解析的 provider。
     */
    fun selectSessionProvider(providerConfigId: String?, model: String?) {
        val sessionId = currentSessionId.value ?: return
        viewModelScope.launch {
            sessionRepository.updateProviderBinding(sessionId, providerConfigId, model)
        }
    }

    /**
     * 发送消息:用户消息落库 → 追加 ASSISTANT/STREAMING 条目 → 流式请求 →
     * 增量在内存聚合([streamingTexts] 供 UI 实时渲染)→ 流结束一次性收敛
     * COMPLETED(失败则 FAILED)。resolve 失败(无绑定/无 key)直接 FAILED 落库,
     * 不发起网络。生成中重复发送被拒绝(UI 已禁用输入,此处兜底)。
     */
    fun sendMessage(content: String) {
        val sessionId = currentSessionId.value ?: return
        if (_isGenerating.value) return
        _isGenerating.value = true
        activeGeneration = viewModelScope.launch {
            try {
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
                val aggregated = StringBuilder()
                try {
                    aiChatService.chatStream(
                        ChatRequest(model = provider.model, messages = buildContext(history)),
                        provider
                    ).collect { chunk ->
                        aggregated.append(chunk.delta)
                        _streamingTexts.value = _streamingTexts.value + (entry.id to aggregated.toString())
                    }
                    completeEntry(entry.id, aggregated.toString())
                } catch (ce: CancellationException) {
                    // 停止生成:半截内容按 INTERRUPTED 保留(历史事实,不伪装完成)。
                    // 写库必须在 NonCancellable 里完成,否则取消中的协程无法再挂起。
                    interruptEntry(entry.id, aggregated.toString())
                    throw ce
                } catch (e: Exception) {
                    failEntry(entry.id, classifyError(e))
                } finally {
                    _streamingTexts.value = _streamingTexts.value - entry.id
                }
            } finally {
                _isGenerating.value = false
                activeGeneration = null
            }
        }
    }

    /** 停止当前生成:已生成的半截文本以 INTERRUPTED 落库,随后输入恢复可用。 */
    fun stopGenerating() {
        activeGeneration?.cancel()
    }

    /**
     * 解析会话生效的 provider 配置与 key。会话绑定优先;未绑定(创建早于默认
     * 设置/迁移而来的存量会话)或绑定失效(配置已被删)时回退全局默认,回退
     * 解析成功后把绑定写回会话 —— 等价"出生继承"在首次使用时补发生,此后
     * 全局默认变化不再影响本会话。两级都无法解析或无 key 时返回 null。
     */
    private suspend fun resolveProvider(sessionId: String): ResolvedProvider? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val providers = aiProviderRepository.settings.firstOrNull()?.providers.orEmpty()

        val bound: Pair<ProviderConfig, String>? = providers
            .firstOrNull { it.id == session.providerConfigId }
            ?.let { config -> session.model?.let { model -> config to model } }

        val resolved = bound ?: run {
            val default = aiProviderRepository.defaultSelection.firstOrNull() ?: return null
            val config = providers.firstOrNull { it.id == default.providerConfigId } ?: return null
            val model = default.model ?: return null
            config to model
        }
        val (config, model) = resolved

        val apiKey = aiProviderRepository.readApiKey(config.id) ?: return null

        if (session.providerConfigId != config.id || session.model != model) {
            // 定向 UPDATE 只改绑定列,不携带旧行整行覆盖 title/updated_at。
            sessionRepository.updateProviderBinding(sessionId, config.id, model)
        }

        return ResolvedProvider(
            configId = config.id,
            providerType = config.providerType,
            displayName = config.displayName,
            endpoint = config.endpoint,
            apiKey = apiKey,
            model = model
        )
    }

    private fun deriveSessionProviderUi(
        session: SessionEntity?,
        providers: List<ProviderConfig>,
        default: DefaultProviderSelection
    ): SessionProviderUiState {
        fun selectionFor(configId: String?, model: String?): SessionProviderSelection? {
            if (configId == null || model == null) return null
            val config = providers.firstOrNull { it.id == configId } ?: return null
            return SessionProviderSelection(
                providerConfigId = config.id,
                displayName = config.displayName,
                iconRes = iconFor(config.providerType),
                model = model
            )
        }

        // 绑定指向已删配置时按未绑定处理,与 resolveProvider 的回退规则一致。
        val bound = session?.let { selectionFor(it.providerConfigId, it.model) }
        val defaultSelection = if (default.isSet) {
            selectionFor(default.providerConfigId, default.model)
        } else {
            null
        }
        return SessionProviderUiState(
            current = bound ?: defaultSelection,
            followsDefault = bound == null,
            defaultSelection = defaultSelection,
            options = providers.map { config ->
                SessionProviderOption(
                    config = config,
                    iconRes = iconFor(config.providerType),
                    hasApiKey = aiProviderRepository.hasApiKey(config.id)
                )
            }
        )
    }

    private fun iconFor(providerType: String): Int = providerRegistry.definitions
        .firstOrNull { it.type == providerType }?.iconRes
        ?: R.drawable.provider_default

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

    /**
     * 停止生成时的收敛:半截内容按 INTERRUPTED 落库。必须在 NonCancellable
     * 上下文执行 —— 取消中的协程在下一个挂起点会再次抛出 CancellationException。
     */
    private suspend fun interruptEntry(entryId: String, partialContent: String) {
        withContext(NonCancellable) {
            messageRepository.updatePayloadAndStatus(
                entryId = entryId,
                content = partialContent,
                newStatus = MessageStatus.INTERRUPTED,
                allowedFrom = listOf(MessageStatus.STREAMING)
            )
        }
    }

    /**
     * 失败归类:已知类别落稳定错误码(UI 映射本地化文案),未知异常保留
     * 原始诊断信息。归类只认异常类型与 HTTP 状态,不解析具体报文。
     */
    internal fun classifyError(e: Exception): String = when {
        e is AiChatProtocolException && (e.statusCode?.value == 401 || e.statusCode?.value == 403) -> ERROR_AUTH
        e is AiChatProtocolException && e.statusCode != null -> ERROR_HTTP
        e is AiChatProtocolException -> ERROR_PROTOCOL
        e is IOException -> ERROR_NETWORK
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val CONTEXT_WINDOW = 20

        /**
         * FAILED 条目 error 字段的稳定错误码,UI 层据此映射本地化文案;
         * 未知值(旧数据/未归类异常)按诊断原文展示。
         */
        const val ERROR_NO_PROVIDER = "no_provider"
        const val ERROR_NETWORK = "network"
        const val ERROR_AUTH = "auth"
        const val ERROR_HTTP = "http"
        const val ERROR_PROTOCOL = "protocol"
    }
}
