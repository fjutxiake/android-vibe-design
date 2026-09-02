package com.aeibi.design.ai

import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.kotlinx.KotlinxSerializer
import com.aeibi.design.ai.tools.WorkspaceTools
import com.aeibi.design.data.projectfiles.ProjectFileTools
import com.aeibi.design.data.sessions.InMemorySessionDao
import com.aeibi.design.data.sessions.SessionRepository
import java.nio.file.Files
import kotlin.reflect.full.valueParameters
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogAgentRunnerTest {
    @Test
    fun executesWriteToolAndReturnsFinalResponseInEventOrder() = runTest {
        val workspace = Files.createTempDirectory("koog-agent-runner-test")
        try {
            val tools = WorkspaceTools(ProjectFileTools(workspace.toFile()))

            @Suppress("UNCHECKED_CAST")
            val writeTool = tools.getTool("write_file") as ToolFromCallable<String>
            val arguments = ToolFromCallable.Args(
                writeTool.callable.valueParameters.associateWith { parameter ->
                    when (parameter.name) {
                        "path" -> "index.html"
                        "content" -> "<h1>Hello</h1>"
                        else -> error("Unexpected tool parameter: ${parameter.name}")
                    }
                }
            )
            val executor = getMockExecutor(KotlinxSerializer()) {
                mockLLMToolCall(writeTool, arguments) onRequestEquals "Create the page"
                mockLLMStream(
                    flowOf(
                        StreamFrame.ReasoningDelta(text = "Checking the result. ", index = 0),
                        StreamFrame.ReasoningComplete(
                            id = null,
                            content = listOf("Checking the result."),
                            index = 0
                        ),
                        StreamFrame.TextDelta("Created ", index = 0),
                        StreamFrame.TextDelta("the page.", index = 0),
                        StreamFrame.TextComplete("Created the page.", index = 0),
                        StreamFrame.End(finishReason = "stop")
                    )
                ) onRequestContains "Updated index.html"
            }
            val events = mutableListOf<AgentEvent>()
            val sessionRepository = SessionRepository(InMemorySessionDao())
            var storedAssistantMessages = 0

            val result = try {
                executeKoogAgent(
                    promptExecutor = executor,
                    model = TEST_MODEL,
                    workspaceTools = tools,
                    sessionRepository = sessionRepository,
                    sessionId = "session",
                    turnId = "turn",
                    input = "Create the page",
                    onEvent = events::add,
                    onAssistantMessageStored = { storedAssistantMessages++ }
                )
            } finally {
                executor.close()
            }

            assertEquals("Created the page.", result)
            assertEquals(
                listOf("Create the page", "Created the page."),
                sessionRepository.loadModelMessages("session").map { it.textContent() }.filter(String::isNotBlank)
            )
            assertEquals("<h1>Hello</h1>", workspace.resolve("index.html").toFile().readText())
            assertEquals(2, storedAssistantMessages)
            assertEquals(
                listOf(
                    AgentEvent.ResponseStarted,
                    AgentEvent.ToolStarted("write_file"),
                    AgentEvent.ToolFinished("write_file"),
                    AgentEvent.ResponseStarted,
                    AgentEvent.ReasoningDelta("Checking the result. "),
                    AgentEvent.TextDelta("Created "),
                    AgentEvent.TextDelta("the page.")
                ),
                events
            )
        } finally {
            assertTrue(workspace.toFile().deleteRecursively())
        }
    }

    @Test
    fun cancelsAnActiveTextStream() = runTest {
        val workspace = Files.createTempDirectory("koog-agent-cancellation-test")
        val executor = getMockExecutor(KotlinxSerializer()) {
            mockLLMStream(
                flow {
                    emit(StreamFrame.TextDelta("Partial response", index = 0))
                    awaitCancellation()
                }
            ) onRequestEquals "Keep streaming"
        }
        val events = mutableListOf<AgentEvent>()
        try {
            val job = launch {
                executeKoogAgent(
                    promptExecutor = executor,
                    model = TEST_MODEL,
                    workspaceTools = WorkspaceTools(ProjectFileTools(workspace.toFile())),
                    sessionRepository = SessionRepository(InMemorySessionDao()),
                    sessionId = "cancel-session",
                    turnId = "turn",
                    input = "Keep streaming",
                    onEvent = events::add
                )
            }

            testScheduler.runCurrent()
            assertEquals(
                listOf(AgentEvent.ResponseStarted, AgentEvent.TextDelta("Partial response")),
                events
            )

            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally {
            executor.close()
            assertTrue(workspace.toFile().deleteRecursively())
        }
    }

    private companion object {
        val TEST_MODEL = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model",
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.OpenAIEndpoint.Completions
            )
        )
    }
}
