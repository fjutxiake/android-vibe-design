package com.aeibi.design.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.messages.MessageEntity
import com.aeibi.design.data.messages.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 聊天状态入口：跟随当前会话展示消息流，重开会话可完整恢复历史消息。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(private val messageRepository: MessageRepository) : ViewModel() {

    private val currentSessionId = MutableStateFlow<String?>(null)

    /** 当前会话的消息列表；sessionId 变化时自动切换到新会话的数据源。 */
    val messages: StateFlow<List<MessageEntity>> = currentSessionId
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
}
