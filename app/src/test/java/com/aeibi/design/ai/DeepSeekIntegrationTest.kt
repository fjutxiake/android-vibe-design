package com.aeibi.design.ai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.aeibi.design.ai.tools.WorkspaceTools
import com.aeibi.design.data.projectfiles.ProjectFileTools
import com.aeibi.design.data.sessions.InMemorySessionDao
import com.aeibi.design.data.sessions.SessionRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DeepSeekIntegrationTest {
    @Test
    fun completesRealWorkspaceToolCall() = runTest(timeout = TEST_TIMEOUT) {
        val apiKey = System.getenv(API_KEY_ENV).orEmpty()
        assumeTrue("Set $API_KEY_ENV to run the real provider test", apiKey.isNotBlank())

        val workspace = Files.createTempDirectory("deepseek-integration-test")
        val client = DeepSeekLLMClient(
            apiKey = apiKey,
            settings = DeepSeekClientSettings(),
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(OkHttp))
        )
        val executor = MultiLLMPromptExecutor(client)
        try {
            val events = mutableListOf<AgentEvent>()
            val response = executeKoogAgent(
                promptExecutor = executor,
                model = DEEPSEEK_CHAT_MODEL,
                workspaceTools = WorkspaceTools(ProjectFileTools(workspace.toFile())),
                sessionRepository = SessionRepository(InMemorySessionDao()),
                sessionId = "deepseek-integration",
                turnId = "turn",
                input = TEST_PROMPT,
                onEvent = events::add
            )

            assertEquals("KOOG_DEEPSEEK_OK", workspace.resolve("integration.txt").toFile().readText())
            assertTrue(response.isNotBlank())
            assertTrue(events.contains(AgentEvent.ToolStarted("write_file")))
            assertTrue(events.contains(AgentEvent.ToolFinished("write_file")))
            assertTrue(events.any { it is AgentEvent.TextDelta && it.text.isNotBlank() })
        } finally {
            executor.close()
            workspace.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val API_KEY_ENV = "DEEPSEEK_API_KEY"
        const val TEST_PROMPT =
            "Use write_file to create integration.txt with the exact content KOOG_DEEPSEEK_OK, then confirm."
        val TEST_TIMEOUT = kotlin.time.Duration.parse("2m")
        val DEEPSEEK_CHAT_MODEL = LLModel(
            provider = LLMProvider.DeepSeek,
            id = "deepseek-chat",
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice
            )
        )
    }
}
