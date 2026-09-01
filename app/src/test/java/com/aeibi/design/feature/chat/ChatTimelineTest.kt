package com.aeibi.design.feature.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
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
    fun cancelledTurnRetainsPartialAssistantText() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage("session", "turn", MessageOrigin.USER, Message.User("Hello", RequestMetaInfo.Empty))
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.CANCELLED,
            partialResponse = "Partial answer"
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message("1", ChatRole.USER, "Hello"),
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
                "The user interrupted the previous turn. Do not assume its response or tool calls completed."
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
