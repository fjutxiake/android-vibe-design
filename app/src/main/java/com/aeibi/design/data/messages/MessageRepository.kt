package com.aeibi.design.data.messages

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MessageRepository @Inject constructor(private val messageDao: MessageDao) {

    /** 观察指定会话的完整消息流,Room 数据变化会自动推送。 */
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> = messageDao.observeMessages(sessionId)

    /** 读取指定会话的完整消息列表,用于后续把历史回合提供给 AI。 */
    suspend fun getMessages(sessionId: String): List<MessageEntity> = messageDao.getMessages(sessionId)

    suspend fun saveMessage(message: MessageEntity) {
        messageDao.upsertMessage(message.copy(createdAt = nextTimestamp(message.createdAt)))
    }

    suspend fun deleteMessagesForSession(sessionId: String): Int = messageDao.deleteMessagesForSession(sessionId)

    suspend fun deleteMessagesForProject(projectId: String): Int = messageDao.deleteMessagesForProject(projectId)

    /**
     * 同一毫秒内连续写入(如用户消息与紧接着的回复)时保持 created_at 严格递增,
     * 否则按 created_at 排序时先后顺序不稳定。
     */
    private fun nextTimestamp(candidate: Long): Long {
        lastTimestamp = maxOf(candidate, lastTimestamp + 1)
        return lastTimestamp
    }

    private var lastTimestamp: Long = 0L
}
