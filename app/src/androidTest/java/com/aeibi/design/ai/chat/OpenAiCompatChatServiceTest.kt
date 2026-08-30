package com.aeibi.design.ai.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MockEngine 驱动的协议层测试:不触网,只验证请求形状与响应解析。 */
class OpenAiCompatChatServiceTest {

    private val provider = ResolvedProvider(
        configId = "cfg",
        providerType = "openai_compatible",
        displayName = "测试服务",
        endpoint = "https://api.example.com/v1",
        apiKey = "sk-test",
        model = "test-model"
    )

    private val request = ChatRequest(
        model = "test-model",
        messages = listOf(
            ChatMessage(ChatMessage.ROLE_USER, "你好"),
            ChatMessage(ChatMessage.ROLE_ASSISTANT, "你好,有什么可以帮你?")
        )
    )

    private lateinit var capturedRequest: HttpRequestData

    private fun service(status: HttpStatusCode = HttpStatusCode.OK, body: String): OpenAiCompatChatService =
        OpenAiCompatChatService(
            HttpClient(
                MockEngine { request ->
                    capturedRequest = request
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )
        )

    private fun capturedBody(): String = (capturedRequest.body as TextContent).text

    @Test
    fun chat_parsesContentFromChoices() = runTest {
        val service = service(
            body = """{"choices":[{"message":{"role":"assistant","content":"好的,天气卡片如下"}}]}"""
        )

        val response = service.chat(request, provider)

        assertEquals("好的,天气卡片如下", response.content)
    }

    @Test
    fun chat_sendsExpectedRequestShape() = runTest {
        val service = service(body = """{"choices":[{"message":{"content":"ok"}}]}""")

        service.chat(request, provider)

        assertEquals("https://api.example.com/v1/chat/completions", capturedRequest.url.toString())
        assertEquals("Bearer sk-test", capturedRequest.headers[HttpHeaders.Authorization])
        assertEquals("application/json", capturedRequest.headers[HttpHeaders.ContentType])
        val body = capturedBody()
        assertTrue("请求体应含 model", body.contains("\"model\":\"test-model\""))
        assertTrue("非流式请求 stream=false", body.contains("\"stream\":false"))
        assertTrue("请求体应含多轮消息", body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"role\":\"assistant\""))
    }

    @Test
    fun chat_whenHttpError_throwsProtocolExceptionWithStatus() = runTest {
        val service = service(
            status = HttpStatusCode.Unauthorized,
            body = """{"error":{"message":"invalid api key"}}"""
        )

        val error = runCatching { service.chat(request, provider) }.exceptionOrNull()

        assertTrue("非 2xx 应抛协议异常", error is AiChatProtocolException)
        assertEquals(HttpStatusCode.Unauthorized, (error as AiChatProtocolException).statusCode)
    }

    @Test
    fun chat_whenContentMissing_throwsProtocolException() = runTest {
        val service = service(body = """{"choices":[]}""")

        val error = runCatching { service.chat(request, provider) }.exceptionOrNull()

        assertTrue("缺失 choices[0].message.content 应抛协议异常", error is AiChatProtocolException)
    }

    @Test
    fun chatStream_emitsDeltasAndStopsAtDone() = runTest {
        val sse = buildString {
            append(": keep-alive\n\n")
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n")
            append("\n") // 空行:事件分隔
            append("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n")
            append("data: [DONE]\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"终止后不应到达\"}}]}\n")
        }
        val service = service(body = sse)

        val chunks = service.chatStream(request, provider).toList()

        // role-only 首块无 content,不发射;[DONE] 后流正常完结,不再消费后续行。
        assertEquals(listOf("你", "好"), chunks.map { it.delta })
    }

    @Test
    fun chatStream_sendsStreamFlagAndAuth() = runTest {
        val service = service(body = "data: [DONE]\n\n")

        service.chatStream(request, provider).toList()

        assertEquals("https://api.example.com/v1/chat/completions", capturedRequest.url.toString())
        assertEquals("Bearer sk-test", capturedRequest.headers[HttpHeaders.Authorization])
        assertTrue("流式请求 stream=true", capturedBody().contains("\"stream\":true"))
    }

    @Test
    fun chatStream_whenMalformedChunk_throwsProtocolException() = runTest {
        val service = service(body = "data: {not-json\n\n")

        val error = runCatching { service.chatStream(request, provider).toList() }.exceptionOrNull()

        assertTrue("非法 SSE chunk 应抛协议异常", error is AiChatProtocolException)
    }

    @Test
    fun chatStream_whenHttpError_throwsProtocolException() = runTest {
        val service = service(
            status = HttpStatusCode.Unauthorized,
            body = """{"error":{"message":"invalid api key"}}"""
        )

        val error = runCatching { service.chatStream(request, provider).toList() }.exceptionOrNull()

        assertTrue("非 2xx 应抛协议异常", error is AiChatProtocolException)
    }
}
