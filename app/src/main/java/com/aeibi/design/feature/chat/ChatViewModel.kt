package com.aeibi.design.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.data.messages.MessageEntryType
import com.aeibi.design.data.messages.MessagePayload
import com.aeibi.design.data.messages.MessageRepository
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 聊天状态入口：跟随当前会话展示消息流，重开会话可完整恢复历史消息。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(private val messageRepository: MessageRepository) : ViewModel() {

    private val currentSessionId = MutableStateFlow<String?>(null)

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
     * 把用户消息写入当前会话(与 touch 会话同一事务)。AI 回复的生成在 #23 中接入,
     * 届时以 ASSISTANT/STREAMING 起始条目追加,再经状态转换收敛到终态。
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
        }
    }
}
