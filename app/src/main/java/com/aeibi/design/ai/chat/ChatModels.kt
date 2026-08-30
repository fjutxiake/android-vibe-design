package com.aeibi.design.ai.chat

import kotlinx.serialization.Serializable

/** 聊天请求的组成:模型 + 多轮上下文(OpenAI 兼容格式)。 */
@Serializable
data class ChatRequest(val model: String, val messages: List<ChatMessage>) {
    init {
        require(model.isNotBlank()) { "model 不能为空" }
    }
}

@Serializable
data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

/** 非流式响应。 */
@Serializable
data class ChatResponse(val content: String)

/** 流式增量。 */
@Serializable
data class ChatChunk(val delta: String)

/** 发送时刻解析出的 provider 快照:endpoint/key 来自配置,model 来自会话绑定。 */
data class ResolvedProvider(
    val configId: String,
    val providerType: String,
    val displayName: String,
    val endpoint: String,
    val apiKey: String,
    val model: String
)
