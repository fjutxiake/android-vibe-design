package com.aeibi.design.data.sessions

import com.aeibi.design.data.messages.MessageRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(private val sessionDao: SessionDao) {

    fun observeSessions(projectId: String): Flow<List<SessionEntity>> = sessionDao.observeSessions(projectId)

    suspend fun getSession(sessionId: String): SessionEntity? = sessionDao.getSession(sessionId)

    suspend fun saveSession(session: SessionEntity) {
        sessionDao.upsertSession(session)
    }

    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Boolean =
        sessionDao.renameSession(sessionId, title, updatedAt) > 0

    suspend fun touchSession(sessionId: String, updatedAt: Long): Boolean =
        sessionDao.touchSession(sessionId, updatedAt) > 0

    suspend fun deleteSession(sessionId: String): Boolean = sessionDao.deleteSession(sessionId) > 0

    suspend fun deleteSessionsForProject(projectId: String): Int = sessionDao.deleteSessionsForProject(projectId)

    /** 单事务删除会话及其消息;消息先显式清理,FK 级联兜底,两步原子。 */
    suspend fun deleteSessionWithMessages(sessionId: String, messageRepository: MessageRepository): Boolean =
        sessionDao.deleteSessionWithMessages(
            sessionId,
            object : com.aeibi.design.data.messages.MessageDao by messageRepository.dao {}
        ) > 0
}
