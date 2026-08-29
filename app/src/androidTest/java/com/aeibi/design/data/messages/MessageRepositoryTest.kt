package com.aeibi.design.data.messages

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.sessions.SessionEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun repository(): MessageRepository = MessageRepository(database.messageDao(), database.sessionDao())

    private fun payload(
        role: MessageRole = MessageRole.USER,
        status: MessageStatus = MessageStatus.COMPLETED,
        content: String
    ) = MessagePayload(role = role, status = status, content = content)

    private suspend fun seedSession(id: String, projectId: String, updatedAt: Long = 1L) {
        database.sessionDao().upsertSession(
            SessionEntity(
                id = id,
                projectId = projectId,
                title = "会话 $id",
                createdAt = 1L,
                updatedAt = updatedAt
            )
        )
    }

    private suspend fun append(
        repo: MessageRepository,
        sessionId: String,
        type: MessageEntryType = MessageEntryType.USER_MESSAGE,
        payload: MessagePayload,
        createdAt: Long = System.currentTimeMillis()
    ): MessageEntry = repo.appendMessage(
        sessionId = sessionId,
        type = type,
        payload = payload,
        id = UUID.randomUUID().toString(),
        createdAt = createdAt
    )

    @Test
    fun appendMessage_persistsPayloadRoundtrip() = runTest {
        val repo = repository()
        seedSession("s1", "p1")

        append(
            repo,
            "s1",
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = payload(MessageRole.ASSISTANT, MessageStatus.INTERRUPTED, "生成中")
        )

        val stored = repo.getMessages("s1")
        assertEquals(1, stored.size)
        assertEquals(MessageEntryType.ASSISTANT_MESSAGE, stored.single().type)
        assertEquals(MessageRole.ASSISTANT, stored.single().role)
        assertEquals(MessageStatus.INTERRUPTED, stored.single().status)
        assertEquals("生成中", stored.single().content)
    }

    @Test
    fun appendMessage_assignsSessionLocalMonotonicSeq() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        seedSession("s2", "p1")

        append(repo, "s1", payload = payload(content = "第一条"), createdAt = 100L)
        append(repo, "s1", payload = payload(content = "第二条"), createdAt = 100L)
        append(repo, "s2", payload = payload(content = "其他会话"), createdAt = 100L)

        val s1 = repo.getMessages("s1")
        assertEquals(listOf("第一条", "第二条"), s1.map { it.content })
        assertEquals(listOf(1L, 2L), s1.map { it.seq })
        // seq 是会话内计数,不同会话互不影响。
        assertEquals(1L, repo.getMessages("s2").single().seq)
    }

    @Test
    fun appendMessage_touchesSessionAtomically() = runTest {
        val repo = repository()
        seedSession("s1", "p1", updatedAt = 100L)

        append(repo, "s1", payload = payload(content = "提问"), createdAt = 500L)

        val session = database.sessionDao().getSession("s1")
        assertEquals(500L, session!!.updatedAt)
    }

    @Test
    fun appendMessage_concurrentAppendsGetDistinctSeq() {
        // runTest 的虚拟时间会把 async 变串行,这里要真并发:多个线程同时抢
        // 同一会话的 seq 分配,UNIQUE(session_id, seq) 必须兜住,事务重试兜底。
        val repo = repository()
        runBlocking {
            seedSession("s1", "p1")

            val results = (1..8).map { i ->
                async(Dispatchers.IO) {
                    repo.appendMessage(
                        sessionId = "s1",
                        type = MessageEntryType.USER_MESSAGE,
                        payload = payload(content = "并发 $i"),
                        id = UUID.randomUUID().toString(),
                        createdAt = 1L
                    )
                }
            }.awaitAll()

            val seqs = results.map { it.seq }
            assertEquals((1L..8L).toList(), seqs.sorted())

            val stored = repo.getMessages("s1")
            assertEquals(8, stored.size)
            assertEquals((1L..8L).toList(), stored.map { it.seq })
        }
    }

    @Test
    fun appendMessage_unknownSessionFailsByForeignKey() = runTest {
        val repo = repository()

        val result = runCatching {
            append(repo, "不存在的会话", payload = payload(content = "无宿主"))
        }

        assertTrue("FK 应拒绝无宿主会话的写入", result.isFailure)
    }

    @Test
    fun updateAssistantStatus_allowsStreamingToTerminalTransition() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        val entry = append(
            repo,
            "s1",
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = payload(MessageRole.ASSISTANT, MessageStatus.STREAMING, "流式输出中")
        )

        val transitioned = repo.updateAssistantStatus(
            entry.id,
            MessageStatus.COMPLETED,
            allowedFrom = listOf(MessageStatus.STREAMING)
        )

        assertTrue(transitioned)
        assertEquals(MessageStatus.COMPLETED, repo.getMessages("s1").single().status)
    }

    @Test
    fun updateAssistantStatus_rejectsTransitionFromDisallowedState() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        val entry = append(
            repo,
            "s1",
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = payload(MessageRole.ASSISTANT, MessageStatus.COMPLETED, "已完成")
        )

        // 已终态的条目不允许被迟到写入者改写。
        val transitioned = repo.updateAssistantStatus(
            entry.id,
            MessageStatus.FAILED,
            allowedFrom = listOf(MessageStatus.STREAMING)
        )

        assertTrue(!transitioned)
        assertEquals(MessageStatus.COMPLETED, repo.getMessages("s1").single().status)
    }

    @Test
    fun reconcileInterruptedEntries_marksStreamingAsInterruptedOnly() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        append(
            repo,
            "s1",
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = payload(MessageRole.ASSISTANT, MessageStatus.STREAMING, "中途退出遗留")
        )
        append(
            repo,
            "s1",
            type = MessageEntryType.ASSISTANT_MESSAGE,
            payload = payload(MessageRole.ASSISTANT, MessageStatus.COMPLETED, "正常完成")
        )
        append(repo, "s1", payload = payload(MessageRole.USER, MessageStatus.COMPLETED, "用户消息"))

        val reconciled = repo.reconcileInterruptedEntries()

        assertEquals(1, reconciled)
        val statuses = repo.getMessages("s1").map { it.status }
        assertEquals(
            listOf(MessageStatus.INTERRUPTED, MessageStatus.COMPLETED, MessageStatus.COMPLETED),
            statuses
        )
    }

    @Test
    fun deleteSession_cascadesMessagesByForeignKey() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        seedSession("s2", "p1")
        append(repo, "s1", payload = payload(content = "s1 消息"))
        append(repo, "s2", payload = payload(content = "s2 消息"))

        database.sessionDao().deleteSession("s1")

        assertTrue(repo.getMessages("s1").isEmpty())
        assertEquals(listOf("s2 消息"), repo.getMessages("s2").map { it.content })
    }

    @Test
    fun deleteSessionsForProject_cascadesMessagesAcrossProject() = runTest {
        val repo = repository()
        seedSession("s1", "p1")
        seedSession("s2", "p1")
        seedSession("s3", "p2")
        append(repo, "s1", payload = payload(content = "p1 会话一"))
        append(repo, "s2", payload = payload(content = "p1 会话二"))
        append(repo, "s3", payload = payload(content = "p2 会话"))

        database.sessionDao().deleteSessionsForProject("p1")

        assertTrue(repo.getMessages("s1").isEmpty())
        assertTrue(repo.getMessages("s2").isEmpty())
        assertEquals(listOf("p2 会话"), repo.getMessages("s3").map { it.content })
    }

    @Test
    fun observeMessages_survivesRestartWithOrderingIntact() = runTest {
        // 用两个独立的数据库实例模拟进程重启:写入端关闭后,读取端重新打开。
        val dbName = "test-restart-${UUID.randomUUID()}.db"
        val writerDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val writerRepo = MessageRepository(writerDb.messageDao(), writerDb.sessionDao())
        seedSessionWith(writerDb.sessionDao(), "s1", "p1")
        writerRepo.appendMessage(
            "s1",
            MessageEntryType.USER_MESSAGE,
            payload(content = "第一条"),
            UUID.randomUUID().toString(),
            100L
        )
        writerRepo.appendMessage(
            "s1",
            MessageEntryType.ASSISTANT_MESSAGE,
            payload(MessageRole.ASSISTANT, MessageStatus.STREAMING, "第二条"),
            UUID.randomUUID().toString(),
            100L
        )
        writerDb.close()

        val readerDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val readerRepo = MessageRepository(readerDb.messageDao(), readerDb.sessionDao())

        val restored = readerRepo.getMessages("s1")
        assertEquals(listOf("第一条", "第二条"), restored.map { it.content })
        assertEquals(listOf(1L, 2L), restored.map { it.seq })

        // 重启后 reconcile 收敛非终态。
        assertEquals(1, readerRepo.reconcileInterruptedEntries())
        readerDb.close()
    }

    private suspend fun seedSessionWith(dao: com.aeibi.design.data.sessions.SessionDao, id: String, projectId: String) {
        dao.upsertSession(
            SessionEntity(
                id = id,
                projectId = projectId,
                title = "会话 $id",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }
}
