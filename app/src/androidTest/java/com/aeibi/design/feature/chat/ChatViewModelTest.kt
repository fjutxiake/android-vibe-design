package com.aeibi.design.feature.chat

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.ai.chat.AiChatProtocolException
import com.aeibi.design.ai.chat.AiChatService
import com.aeibi.design.ai.chat.ChatChunk
import com.aeibi.design.ai.chat.ChatRequest
import com.aeibi.design.ai.chat.ResolvedProvider
import com.aeibi.design.ai.provider.AiProviderRegistry
import com.aeibi.design.ai.provider.DeepSeekProvider
import com.aeibi.design.ai.provider.OpenAiProvider
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.data.messages.MessageEntryEntity
import com.aeibi.design.data.messages.MessageEntryType
import com.aeibi.design.data.messages.MessagePayload
import com.aeibi.design.data.messages.MessagePayloadCodec
import com.aeibi.design.data.messages.MessageRepository
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import com.aeibi.design.data.securestore.SecureStore
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var aiProviderRepository: AiProviderRepository
    private lateinit var fakeService: FakeAiChatService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiProviderRepository = AiProviderRepository(context, FakeSecureStore())
        fakeService = FakeAiChatService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(): ChatViewModel = ChatViewModel(
        MessageRepository(database.messageDao(), database.sessionDao()),
        SessionRepository(database.sessionDao()),
        aiProviderRepository,
        fakeService,
        AiProviderRegistry(OpenAiProvider(), DeepSeekProvider())
    )

    private suspend fun seedSession(
        id: String,
        projectId: String = "p1",
        updatedAt: Long = 100L,
        providerConfigId: String? = null,
        model: String? = null
    ) {
        database.sessionDao().upsertSession(
            SessionEntity(
                id = id,
                projectId = projectId,
                title = "会话 $id",
                createdAt = 1L,
                updatedAt = updatedAt,
                providerConfigId = providerConfigId,
                model = model
            )
        )
    }

    private suspend fun seedProvider(configId: String = "cfg-1", model: String = "test-model") {
        aiProviderRepository.saveProvider(
            ProviderConfig(
                id = configId,
                providerType = "openai_compatible",
                displayName = "测试服务",
                endpoint = "https://api.example.com/v1",
                models = listOf(model)
            ),
            apiKey = "sk-test"
        )
    }

    private fun entry(
        seq: Long,
        role: MessageRole,
        status: MessageStatus,
        content: String,
        sessionId: String = "s1"
    ): MessageEntry = MessageEntry(
        entity = MessageEntryEntity(
            id = "e$seq",
            sessionId = sessionId,
            seq = seq,
            type = if (role == MessageRole.USER) MessageEntryType.USER_MESSAGE else MessageEntryType.ASSISTANT_MESSAGE,
            payload = MessagePayloadCodec.encode(MessagePayload(role = role, status = status, content = content)),
            createdAt = seq
        ),
        payload = MessagePayload(role = role, status = status, content = content)
    )

    @Test
    fun sendMessage_persistsConversationAndCompletesReply() = runTest {
        seedProvider()
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "test-model")
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("做一个天气卡片")
        awaitGenerationDone(viewModel)

        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)

        val user = entries[0]
        assertEquals(MessageEntryType.USER_MESSAGE, user.type)
        val userPayload = MessagePayloadCodec.decode(user.payload)
        assertEquals("做一个天气卡片", userPayload.content)
        assertEquals(MessageRole.USER, userPayload.role)
        assertEquals(MessageStatus.COMPLETED, userPayload.status)

        val assistant = entries[1]
        assertEquals(MessageEntryType.ASSISTANT_MESSAGE, assistant.type)
        val reply = MessagePayloadCodec.decode(assistant.payload)
        assertEquals(MessageRole.ASSISTANT, reply.role)
        assertEquals(MessageStatus.COMPLETED, reply.status)
        assertEquals(FakeAiChatService.CANNED_REPLY_CHUNKS.joinToString(""), reply.content)
        assertEquals("cfg-1", reply.providerConfigId)
        assertEquals("test-model", reply.model)
        assertNull(reply.error)

        // 请求上下文只含刚发送的用户消息;model 取会话绑定值。
        assertEquals(listOf("做一个天气卡片"), fakeService.lastRequest?.messages?.map { it.content })
        assertEquals("test-model", fakeService.lastRequest?.model)
        assertEquals("test-model", fakeService.lastProvider?.model)

        val session = database.sessionDao().getSession("s1")
        assertTrue("会话 updated_at 应被刷新", session!!.updatedAt > 100L)
    }

    @Test
    fun sendMessage_whenNoSessionBound_persistsNothing() = runTest {
        seedSession("s1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.sendMessage("无会话时的输入")

        assertTrue(database.messageDao().getMessages("s1").isEmpty())
        assertEquals(100L, database.sessionDao().getSession("s1")!!.updatedAt)
    }

    @Test
    fun sendMessage_whenNoProviderBound_failsWithoutNetwork() = runTest {
        // DataStore 状态跨用例共享:显式清默认,保证本用例两级解析都为空。
        aiProviderRepository.setDefaultSelection(null, null)
        seedSession("s1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("没有绑定 provider 的输入")
        awaitGenerationDone(viewModel)

        // resolve 失败不发起网络,直接 FAILED 落库。
        assertEquals(0, fakeService.streamCalls)
        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)
        val reply = MessagePayloadCodec.decode(entries[1].payload)
        assertEquals(MessageStatus.FAILED, reply.status)
        assertEquals(ChatViewModel.ERROR_NO_PROVIDER, reply.error)
    }

    @Test
    fun sendMessage_whenSessionUnbound_fallsBackToDefaultAndBinds() = runTest {
        seedProvider()
        aiProviderRepository.setDefaultSelection("cfg-1", "test-model")
        // 存量会话:创建早于默认设置,无绑定。
        seedSession("s1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("存量会话首次发言")
        awaitGenerationDone(viewModel)

        assertEquals(1, fakeService.streamCalls)
        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)
        val reply = MessagePayloadCodec.decode(entries[1].payload)
        assertEquals(MessageStatus.COMPLETED, reply.status)
        assertEquals("cfg-1", reply.providerConfigId)

        // 回退解析成功后绑定写回:此后全局默认变化不再影响本会话。
        val session = database.sessionDao().getSession("s1")
        assertEquals("cfg-1", session!!.providerConfigId)
        assertEquals("test-model", session.model)
    }

    @Test
    fun sendMessage_whenSessionBound_boundWinsOverGlobalDefault() = runTest {
        seedProvider("cfg-1", "bound-model")
        seedProvider("cfg-2", "default-model")
        aiProviderRepository.setDefaultSelection("cfg-2", "default-model")
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "bound-model")
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("绑定优先")
        awaitGenerationDone(viewModel)

        // 会话绑定优先,解析与落库都不被全局默认改写。
        assertEquals("cfg-1", fakeService.lastProvider?.configId)
        assertEquals("bound-model", fakeService.lastProvider?.model)
        val session = database.sessionDao().getSession("s1")
        assertEquals("cfg-1", session!!.providerConfigId)
        assertEquals("bound-model", session.model)
    }

    @Test
    fun sendMessage_whenServiceThrows_marksEntryFailed() = runTest {
        seedProvider()
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "test-model")
        fakeService.error = IllegalStateException("boom")
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("触发服务异常")
        awaitGenerationDone(viewModel)

        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)
        val reply = MessagePayloadCodec.decode(entries[1].payload)
        assertEquals(MessageStatus.FAILED, reply.status)
        assertEquals("boom", reply.error)
    }

    @Test
    fun sendMessage_streamsLiveTextThenConvergesWithSingleWrite() = runTest {
        seedProvider()
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "test-model")
        fakeService.streamChunks = listOf("半", "截")
        val viewModel = viewModel()
        viewModel.bind("s1")

        // 记录 streamingTexts 的全部中间态:验证逐块实时文本存在、且收敛后清空。
        val seenTexts = mutableListOf<Map<String, String>>()
        backgroundScope.launch(dispatcher) { viewModel.streamingTexts.collect { seenTexts.add(it) } }

        viewModel.sendMessage("触发流式")
        awaitGenerationDone(viewModel)

        // 增量只在内存聚合,不逐块落库:仍只有一条 assistant 条目。
        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)
        val reply = MessagePayloadCodec.decode(entries[1].payload)
        assertEquals(MessageStatus.COMPLETED, reply.status)
        assertEquals("半截", reply.content)

        assertTrue("收敛后实时文本应清空", viewModel.streamingTexts.value.isEmpty())
        val liveStates = seenTexts.mapNotNull { it[entries[1].id] }
        assertEquals(listOf("半", "半截"), liveStates)
    }

    @Test
    fun stopGenerating_keepsPartialReplyInterrupted() = runTest {
        seedProvider()
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "test-model")
        fakeService.streamChunks = listOf("半", "截")
        fakeService.streamGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("中途停止")
        // 等第一个增量已进入实时文本(此后流必然停在门上),再停止。
        awaitCondition { viewModel.streamingTexts.value.values.any { it.isNotEmpty() } }

        viewModel.stopGenerating()
        awaitGenerationDone(viewModel)

        val entries = database.messageDao().getMessages("s1")
        assertEquals(2, entries.size)
        val reply = MessagePayloadCodec.decode(entries[1].payload)
        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals("半截文本应保留", "半", reply.content)
        assertTrue("停止后实时文本应清空", viewModel.streamingTexts.value.isEmpty())
    }

    @Test
    fun sendMessage_rejectedWhileGenerating() = runTest {
        seedProvider()
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "test-model")
        fakeService.streamGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.bind("s1")

        viewModel.sendMessage("第一条")
        awaitCondition { viewModel.streamingTexts.value.values.any { it.isNotEmpty() } }

        viewModel.sendMessage("生成中的第二条")

        // 生成中拒绝新发送:没有第二条用户消息落库。
        val userCount = database.messageDao().getMessages("s1")
            .count { MessagePayloadCodec.decode(it.payload).role == MessageRole.USER }
        assertEquals(1, userCount)

        // 收尾:开门让流走完,避免取消后台协程的噪音。
        fakeService.streamGate?.complete(Unit)
        awaitGenerationDone(viewModel)
    }

    @Test
    fun classifyError_mapsKnownCategoriesToStableCodes() {
        val viewModel = viewModel()

        assertEquals(ChatViewModel.ERROR_NETWORK, viewModel.classifyError(IOException("timeout")))
        assertEquals(
            ChatViewModel.ERROR_AUTH,
            viewModel.classifyError(AiChatProtocolException("x", HttpStatusCode.Unauthorized))
        )
        assertEquals(
            ChatViewModel.ERROR_AUTH,
            viewModel.classifyError(AiChatProtocolException("x", HttpStatusCode.Forbidden))
        )
        assertEquals(
            ChatViewModel.ERROR_HTTP,
            viewModel.classifyError(AiChatProtocolException("x", HttpStatusCode.NotFound))
        )
        assertEquals(ChatViewModel.ERROR_PROTOCOL, viewModel.classifyError(AiChatProtocolException("x")))
        assertEquals("boom", viewModel.classifyError(IllegalStateException("boom")))
    }

    @Test
    fun selectSessionProvider_writesBindingAndNextSendUsesIt() = runTest {
        seedProvider("cfg-1", "model-a")
        seedProvider("cfg-2", "model-b")
        seedSession("s1", updatedAt = 100L)
        val viewModel = viewModel()
        viewModel.bind("s1")
        // WhileSubscribed 状态流:先挂收集器,上游才开始派生。
        backgroundScope.launch(dispatcher) { viewModel.sessionProvider.collect {} }

        viewModel.selectSessionProvider("cfg-2", "model-b")
        awaitCondition { viewModel.sessionProvider.value.current?.providerConfigId == "cfg-2" }

        // 换绑即时生效:下一条消息用新绑定,不需要重进会话。
        viewModel.sendMessage("换绑后发言")
        awaitGenerationDone(viewModel)
        assertEquals("cfg-2", fakeService.lastProvider?.configId)
        assertEquals("model-b", fakeService.lastProvider?.model)
        assertEquals("model-b", viewModel.sessionProvider.value.current?.model)
        assertTrue("已绑定会话不再跟随默认", !viewModel.sessionProvider.value.followsDefault)
    }

    @Test
    fun selectSessionProvider_clearsBackToFollowDefault() = runTest {
        seedProvider("cfg-1", "model-a")
        aiProviderRepository.setDefaultSelection("cfg-1", "model-a")
        seedSession("s1", updatedAt = 100L, providerConfigId = "cfg-1", model = "model-a")
        val viewModel = viewModel()
        viewModel.bind("s1")
        backgroundScope.launch(dispatcher) { viewModel.sessionProvider.collect {} }
        awaitCondition { viewModel.sessionProvider.value.current != null }

        viewModel.selectSessionProvider(null, null)

        // 解除绑定后回到跟随默认,当前值取全局默认。
        awaitCondition { viewModel.sessionProvider.value.followsDefault }
        val state = viewModel.sessionProvider.value
        assertEquals("cfg-1", state.defaultSelection?.providerConfigId)
        assertEquals("cfg-1", state.current?.providerConfigId)
        assertNull(database.sessionDao().getSession("s1")?.providerConfigId)
    }

    @Test
    fun buildContext_keepsUsersAndCompletedOnly() = runTest {
        val viewModel = viewModel()
        val history = listOf(
            entry(1, MessageRole.USER, MessageStatus.COMPLETED, "问题一"),
            entry(2, MessageRole.ASSISTANT, MessageStatus.COMPLETED, "回答一"),
            entry(3, MessageRole.ASSISTANT, MessageStatus.INTERRUPTED, "半截回答不进上下文"),
            entry(4, MessageRole.ASSISTANT, MessageStatus.FAILED, ""),
            entry(5, MessageRole.ASSISTANT, MessageStatus.STREAMING, ""),
            entry(6, MessageRole.USER, MessageStatus.COMPLETED, "问题二")
        )

        val context = viewModel.buildContext(history)

        assertEquals(
            listOf("user" to "问题一", "assistant" to "回答一", "user" to "问题二"),
            context.map { it.role to it.content }
        )
    }

    @Test
    fun buildContext_capsToRecentWindow() = runTest {
        val viewModel = viewModel()
        // 30 条用户消息,窗口只保留最近 20 条。
        val history = (1..30).map { seq ->
            entry(seq.toLong(), MessageRole.USER, MessageStatus.COMPLETED, "消息$seq")
        }

        val context = viewModel.buildContext(history)

        assertEquals(ChatViewModel.CONTEXT_WINDOW, context.size)
        assertEquals("消息11", context.first().content)
        assertEquals("消息30", context.last().content)
    }

    /** 内存版 SecureStore,测试不触碰 Keystore。 */
    private class FakeSecureStore : SecureStore {
        private val map = mutableMapOf<String, String>()

        override fun contains(key: String): Boolean = map.containsKey(key)

        override suspend fun put(key: String, value: String) {
            map[key] = value
        }

        override suspend fun get(key: String): String? = map[key]

        override suspend fun delete(key: String) {
            map.remove(key)
        }
    }

    /**
     * sendMessage 把工作发射进 viewModelScope 后立即返回,后续链路跑在
     * Room/DataStore 的真实调度器上。断言库状态前先等生成结束(isGenerating
     * 在所有写库完成后的 finally 里才落 false),避免依赖竞态时序。
     */
    private fun awaitGenerationDone(viewModel: ChatViewModel) {
        awaitCondition { !viewModel.isGenerating.value }
    }

    /** 轮询等待跨真实调度器才能观察到的条件成立(带超时护栏)。 */
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("等待条件超时", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    /** 可编程的假聊天服务:记录请求,按剧本发射流式增量或抛预设异常。 */
    private class FakeAiChatService : AiChatService {
        var error: Exception? = null
        var streamCalls = 0
        var lastRequest: ChatRequest? = null
        var lastProvider: ResolvedProvider? = null
        var streamChunks: List<String> = CANNED_REPLY_CHUNKS

        /** 非空时每个增量发射后挂起等待,模拟慢速流/中途停止的窗口。 */
        var streamGate: CompletableDeferred<Unit>? = null

        override fun chatStream(request: ChatRequest, provider: ResolvedProvider): Flow<ChatChunk> = flow {
            streamCalls++
            lastRequest = request
            lastProvider = provider
            error?.let { throw it }
            streamChunks.forEach { delta ->
                emit(ChatChunk(delta))
                streamGate?.await()
            }
        }

        companion object {
            val CANNED_REPLY_CHUNKS = listOf("这是", "测试", "回复")
        }
    }
}
