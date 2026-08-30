package com.aeibi.design.ai.chat

import kotlinx.coroutines.flow.Flow

/**
 * provider 无关的聊天服务:上层(ChatViewModel)只面对本接口,不感知具体协议。
 *
 * 实现约定:
 * - OpenAI 兼容格式(/v1/chat/completions),OpenAI 与 DeepSeek 共用一套实现;
 * - [chat] 非流式:一次性返回完整回复;
 * - [chatStream] 流式:逐块发射 delta,正常结束以流完结表示(实现方保证不发射终止符);
 * - 取消:调用方 cancel 收集协程时,实现方须正确传播 CancellationException,不得吞掉;
 * - 错误:网络/协议错误以异常抛出,由调用方映射到 FAILED 状态。
 */
interface AiChatService {

    suspend fun chat(request: ChatRequest, provider: ResolvedProvider): ChatResponse

    fun chatStream(request: ChatRequest, provider: ResolvedProvider): Flow<ChatChunk>
}
