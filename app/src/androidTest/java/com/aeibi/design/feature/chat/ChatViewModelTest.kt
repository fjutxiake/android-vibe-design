package com.aeibi.design.feature.chat

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.messages.MessageEntryType
import com.aeibi.design.data.messages.MessagePayloadCodec
import com.aeibi.design.data.messages.MessageRepository
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import com.aeibi.design.data.sessions.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(): ChatViewModel = ChatViewModel(
        MessageRepository(database.messageDao(), database.sessionDao())
    )

    private suspend fun seedSession(id: String, projectId: String, updatedAt: Long) {
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

    @Test
    fun sendMessage_persistsUserMessageAndTouchesSession() = runTest {
        seedSession("s1", "p1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("做一个天气卡片")

        val entries = database.messageDao().getMessages("s1")
        assertEquals(1, entries.size)
        assertEquals(MessageEntryType.USER_MESSAGE, entries.single().type)
        val payload = MessagePayloadCodec.decode(entries.single().payload)
        assertEquals("做一个天气卡片", payload.content)
        assertEquals(MessageRole.USER, payload.role)
        assertEquals(MessageStatus.COMPLETED, payload.status)
        val session = database.sessionDao().getSession("s1")
        assertTrue("会话 updated_at 应被刷新", session!!.updatedAt > 100L)
    }

    @Test
    fun sendMessage_whenNoSessionBound_persistsNothing() = runTest {
        seedSession("s1", "p1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.sendMessage("无会话时的输入")

        assertTrue(database.messageDao().getMessages("s1").isEmpty())
        assertEquals(100L, database.sessionDao().getSession("s1")!!.updatedAt)
    }
}
