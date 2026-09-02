package com.aeibi.design.feature.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.aeibi.design.data.sessions.AgentFailure
import com.aeibi.design.data.sessions.InMemorySessionDao
import com.aeibi.design.data.sessions.MessageOrigin
import com.aeibi.design.data.sessions.SessionRepository
import com.aeibi.design.data.sessions.TurnStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimelineTest {
    @Test
    fun assistantReasoningIsShownInTimelineOrder() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn",
            origin = MessageOrigin.ASSISTANT,
            message = Message.Assistant(
                parts = listOf(
                    MessagePart.Reasoning(content = "I will inspect the project."),
                    MessagePart.Text("I found the relevant file.")
                ),
                metaInfo = ResponseMetaInfo.Empty
            )
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Thinking("1:thinking:0", "I will inspect the project."),
                ChatTimelineItem.Message("1:text:1", ChatRole.ASSISTANT, "I found the relevant file.")
            ),
            repository.observeEntries("session").first().toTimeline(repository)
        )
    }

    @Test
    fun toolCallAndResultAreShownAsSeparateTimelineItemsInOrder() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn",
            origin = MessageOrigin.ASSISTANT,
            message = Message.Assistant(
                parts = listOf(
                    MessagePart.Tool.Call(id = "call-1", tool = "read_file", args = "{}"),
                    MessagePart.Text("")
                ),
                metaInfo = ResponseMetaInfo.Empty
            )
        )
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn",
            origin = MessageOrigin.TOOL,
            message = Message.User(
                MessagePart.Tool.Result(id = "call-1", tool = "read_file", output = "content"),
                RequestMetaInfo.Empty
            )
        )

        assertEquals(
            listOf(
                ChatTimelineItem.ToolCall("call-1", "read_file"),
                ChatTimelineItem.ToolResult("2:tool-result:0", "read_file", false)
            ),
            repository.observeEntries("session").first().toTimeline(repository)
        )
    }

    @Test
    fun toolErrorIsKeptInTheTimelineResult() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn",
            origin = MessageOrigin.TOOL,
            message = Message.User(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "File not found",
                    isError = true
                ),
                RequestMetaInfo.Empty
            )
        )

        assertEquals(
            listOf(ChatTimelineItem.ToolResult("1:tool-result:0", "read_file", true)),
            repository.observeEntries("session").first().toTimeline(repository)
        )
    }

    @Test
    fun cancelledTurnRetainsPartialAssistantText() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage("session", "turn", MessageOrigin.USER, Message.User("Hello", RequestMetaInfo.Empty))
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.CANCELLED,
            partialResponse = "Partial answer",
            partialReasoning = "Checking the result."
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message("1", ChatRole.USER, "Hello"),
                ChatTimelineItem.Thinking("2:partial-thinking", "Checking the result."),
                ChatTimelineItem.Message(
                    id = "2:partial",
                    role = ChatRole.ASSISTANT,
                    text = "Partial answer",
                    status = ChatMessageStatus.CANCELLED
                )
            ),
            repository.observeEntries("session").first().toTimeline(repository)
        )
        assertEquals(
            listOf(
                "Hello",
                "The previous turn was interrupted on purpose. Any interrupted tool calls may have partially executed. Inspect the workspace before continuing."
            ),
            repository.loadModelMessages("session").map { it.textContent() }
        )
    }

    @Test
    fun failedTurnShowsPartialTextAndOneFailureWithoutModelContextLeak() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage("session", "turn", MessageOrigin.USER, Message.User("Hello", RequestMetaInfo.Empty))
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.FAILED,
            failure = AgentFailure("Network error", "NETWORK"),
            partialResponse = "Partial answer"
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message("1", ChatRole.USER, "Hello"),
                ChatTimelineItem.Message("2:partial", ChatRole.ASSISTANT, "Partial answer"),
                ChatTimelineItem.Message(
                    id = "2",
                    role = ChatRole.ASSISTANT,
                    text = "Network error",
                    status = ChatMessageStatus.FAILED
                )
            ),
            repository.observeEntries("session").first().toTimeline(repository)
        )
        assertEquals(listOf("Hello"), repository.loadModelMessages("session").map { it.textContent() })
    }
}
