package com.aeibi.design.data.messages

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.sessions.SessionEntity
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(): MessageRepository = MessageRepository(database.messageDao())

    private fun message(
        sessionId: String,
        role: MessageRole = MessageRole.USER,
        status: MessageStatus = MessageStatus.COMPLETED,
        content: String,
        createdAt: Long = System.currentTimeMillis()
    ) = MessageEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        role = role,
        status = status,
        content = content,
        createdAt = createdAt
    )

    private suspend fun seedSession(id: String, projectId: String) {
        database.sessionDao().upsertSession(
            SessionEntity(
                id = id,
                projectId = projectId,
                title = "会话 $id",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    @Test
    fun saveMessage_returnsRoleAndStatusRoundtrip() = runTest {
        val repo = repository()

        repo.saveMessage(
            message("s1", role = MessageRole.ASSISTANT, status = MessageStatus.INTERRUPTED, content = "生成中")
        )

        val stored = repo.getMessages("s1")
        assertEquals(1, stored.size)
        assertEquals(MessageRole.ASSISTANT, stored.single().role)
        assertEquals(MessageStatus.INTERRUPTED, stored.single().status)
        assertEquals("生成中", stored.single().content)
    }

    @Test
    fun observeMessages_returnsMessagesInCreationOrder() = runTest {
        val repo = repository()

        repo.saveMessage(message("s1", content = "第一条", createdAt = 100L))
        repo.saveMessage(message("s1", content = "第二条", createdAt = 200L))
        repo.saveMessage(message("s1", content = "第三条", createdAt = 300L))
        repo.saveMessage(message("s2", content = "其他会话", createdAt = 150L))

        val messages = repo.observeMessages("s1").first()
        assertEquals(listOf("第一条", "第二条", "第三条"), messages.map { it.content })
    }

    @Test
    fun saveMessage_bumpsTimestampWhenSameMillisecond() = runTest {
        val repo = repository()

        repo.saveMessage(message("s1", content = "用户提问", createdAt = 1000L))
        repo.saveMessage(message("s1", role = MessageRole.ASSISTANT, content = "回复", createdAt = 1000L))

        val stored = repo.getMessages("s1")
        assertEquals(listOf("用户提问", "回复"), stored.map { it.content })
        assertTrue(stored[1].createdAt > stored[0].createdAt)
    }

    @Test
    fun deleteMessagesForSession_onlyRemovesTargetSessionMessages() = runTest {
        val repo = repository()

        repo.saveMessage(message("s1", content = "s1 消息"))
        repo.saveMessage(message("s2", content = "s2 消息"))

        val deleted = repo.deleteMessagesForSession("s1")

        assertEquals(1, deleted)
        assertEquals(listOf("s2 消息"), repo.getMessages("s2").map { it.content })
        assertTrue(repo.getMessages("s1").isEmpty())
    }

    @Test
    fun deleteMessagesForProject_removesMessagesAcrossProjectSessions() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        seedSession("s2", "p1")
        seedSession("s3", "p2")

        repo.saveMessage(message("s1", content = "p1 会话一"))
        repo.saveMessage(message("s2", content = "p1 会话二"))
        repo.saveMessage(message("s3", content = "p2 会话"))

        val deleted = repo.deleteMessagesForProject("p1")

        assertEquals(2, deleted)
        assertTrue(repo.getMessages("s1").isEmpty())
        assertTrue(repo.getMessages("s2").isEmpty())
        assertEquals(listOf("p2 会话"), repo.getMessages("s3").map { it.content })
    }
}
