package com.aeibi.design.feature.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import com.aeibi.design.data.sessions.InMemorySessionDao
import com.aeibi.design.data.sessions.MessageOrigin
import com.aeibi.design.data.sessions.SessionRepository
import com.aeibi.design.data.sessions.TurnStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnFinishedTest {
    @Test
    fun completedTurnIsDetectedOnceThenIdle() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn-1",
            origin = MessageOrigin.ASSISTANT,
            message = Message.Assistant(
                parts = listOf(MessagePart.Text("Created the page.")),
                metaInfo = ResponseMetaInfo.Empty
            )
        )
        repository.finishTurn("session", "turn-1", TurnStatus.COMPLETE)

        var lastSeenId = -1L
        val (firstId, firstCompleted) = repository.observeEntries("session").first()
            .completedTurnSeen(lastSeenId, repository)
        lastSeenId = firstId
        assertTrue("COMPLETE turn should be detected once", firstCompleted)

        val (_, secondCompleted) = repository.observeEntries("session").first()
            .completedTurnSeen(lastSeenId, repository)
        assertFalse("Same turn must not be re-reported", secondCompleted)
    }

    @Test
    fun nonCompletedTurnsAdvanceIdWithoutReportingCompletion() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.finishTurn("session", "turn-1", TurnStatus.FAILED)
        repository.finishTurn("session", "turn-2", TurnStatus.CANCELLED)

        val entries = repository.observeEntries("session").first()
        val (lastId, completed) = entries.completedTurnSeen(-1L, repository)
        assertEquals("Latest finished turn id is returned", 2L, lastId)
        assertFalse("FAILED/CANCELLED status is not a completion", completed)
    }

    @Test
    fun completedTurnAfterMessagesIsDetected() = runTest {
        val repository = SessionRepository(InMemorySessionDao())
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn-1",
            origin = MessageOrigin.ASSISTANT,
            message = Message.Assistant(
                parts = listOf(MessagePart.Text("Done.")),
                metaInfo = ResponseMetaInfo.Empty
            )
        )
        repository.finishTurn("session", "turn-1", TurnStatus.COMPLETE)
        repository.appendMessage(
            sessionId = "session",
            turnId = "turn-2",
            origin = MessageOrigin.USER,
            message = Message.User("Fix the colors", ai.koog.prompt.message.RequestMetaInfo.Empty)
        )

        val entries = repository.observeEntries("session").first()
        val (id, completed) = entries.completedTurnSeen(-1L, repository)
        assertEquals("Finished entry id is tracked even when a message is appended after it", 2L, id)
        assertTrue(completed)
    }
}
