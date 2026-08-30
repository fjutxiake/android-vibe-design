package com.aeibi.design.ai.chat

import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI 兼容(/chat/completions)实现:OpenAI 与 DeepSeek 共用。
 *
 * 流式用手写 SSE 行解析而非 ktor-sse 插件:chunk 结构简单
 * (data: {json} 行 + data: [DONE] 终止),少一个依赖且行为可控。
 */
class OpenAiCompatChatService(private val client: HttpClient, private val json: Json = DEFAULT_JSON) : AiChatService {

    override fun chatStream(request: ChatRequest, provider: ResolvedProvider): Flow<ChatChunk> = flow {
        // 流式必须用 preparePost + execute:普通 client.post 会在 HttpResponse 返回前
        // 攒齐整个 body(ktor 对非流式请求自动加载并缓存响应体),SSE 逐块到达的
        // 增量会被攒到流结束才可见——表现为"假流式"。execute 的回调内拿到的
        // bodyAsChannel 才是逐字节到达的。
        client.preparePost("${provider.endpoint.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            headers.append("Authorization", "Bearer ${provider.apiKey}")
            setBody(buildRequestBody(request, stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                // 非 2xx:截取错误响应片段供诊断,不进入解析流程。
                val detail = response.bodyAsText().take(200)
                throw AiChatProtocolException("HTTP ${response.status.value}: $detail", response.status)
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isBlank() || line.startsWith(":") -> Unit // 心跳注释行
                    line == "data: $SSE_DONE" -> return@execute // [DONE]:正常终止
                    line.startsWith("data: ") -> {
                        val payload = line.removePrefix("data: ")
                        val chunk = runCatching {
                            json.decodeFromString(StreamChunkBody.serializer(), payload)
                        }.getOrElse { throw AiChatProtocolException("SSE chunk 解析失败: $payload") }
                        val delta = chunk.choices.firstOrNull()?.delta?.content
                        if (!delta.isNullOrEmpty()) emit(ChatChunk(delta))
                    }
                    else -> Unit // 忽略未知行(SSE 规范允许扩展字段)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: ChatRequest, stream: Boolean): String {
        val root = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put(
                "messages",
                kotlinx.serialization.json.buildJsonArray {
                    request.messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            }
                        )
                    }
                }
            )
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    @Serializable
    private data class StreamChunkBody(@SerialName("choices") val choices: List<Choice> = emptyList()) {
        @Serializable
        data class Choice(@SerialName("delta") val delta: Delta? = null) {
            @Serializable
            data class Delta(@SerialName("content") val content: String? = null)
        }
    }

    companion object {
        const val SSE_DONE = "[DONE]"
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/** HTTP 状态非 2xx 或协议结构不符时抛出,消息面向开发者诊断。 */
class AiChatProtocolException(message: String, val statusCode: HttpStatusCode? = null) : Exception(message)
