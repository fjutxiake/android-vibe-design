package com.aeibi.design.data.sessions

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun contextReplacementKeepsOnlyReplacementAndLaterMessagesInModelContext() = runTest {
        val repository = SessionRepository(InMemorySessionDao())

        repository.appendMessage("session", "turn-1", MessageOrigin.USER, Message.User("First", RequestMetaInfo.Empty))
        repository.appendMessage(
            "session",
            "turn-1",
            MessageOrigin.ASSISTANT,
            Message.Assistant("First response", ResponseMetaInfo.Empty)
        )
        repository.replaceContext(
            "session",
            "turn-2",
            listOf(Message.User("Summary", RequestMetaInfo.Empty))
        )
        repository.appendMessage("session", "turn-2", MessageOrigin.USER, Message.User("Second", RequestMetaInfo.Empty))

        assertEquals(
            listOf("Summary", "Second"),
            repository.loadModelMessages("session").map { it.textContent() }
        )
    }

    @Test
    fun interruptedToolCallGetsUnknownOutcomeResult() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            "session",
            "turn",
            MessageOrigin.ASSISTANT,
            Message.Assistant(
                MessagePart.Tool.Call(id = "call-1", tool = "write_file", args = "{}"),
                ResponseMetaInfo.Empty
            )
        )

        repository.repairInterruptedToolCalls("session")

        val result = repository.loadModelMessages("session").last() as Message.User
        val part = result.parts.single() as MessagePart.Tool.Result
        assertEquals("call-1", part.id)
        assertTrue(part.isError)
        assertEquals(
            "The tool execution was interrupted and its outcome is unknown. Inspect the workspace before retrying it.",
            part.output
        )
    }

    @Test
    fun failedTurnKeepsStructuredFailureOutsideModelContext() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage("session", "turn", MessageOrigin.USER, Message.User("Hello", RequestMetaInfo.Empty))
        repository.finishTurn(
            sessionId = "session",
            turnId = "turn",
            status = TurnStatus.FAILED,
            failure = AgentFailure("The selected provider has no API key", "CONFIGURATION")
        )

        val entries = repository.observeEntries("session").first()
        val finished = entries.last()

        assertEquals(
            AgentFailure("The selected provider has no API key", "CONFIGURATION"),
            repository.decodeTurnFinished(finished).failure
        )
        assertEquals(listOf("Hello"), repository.loadModelMessages("session").map { it.textContent() })
    }
}
