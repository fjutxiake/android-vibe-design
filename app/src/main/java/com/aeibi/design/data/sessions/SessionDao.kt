package com.aeibi.design.data.sessions

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query(
        """
      SELECT * FROM sessions
      WHERE project_id = :projectId
      ORDER BY updated_at DESC
    """
    )
    fun observeSessions(projectId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query(
        """
      UPDATE sessions
      SET title = :title, updated_at = :updatedAt
      WHERE id = :sessionId
    """
    )
    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Int

    @Query("UPDATE sessions SET updated_at = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long): Int

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    @Query("DELETE FROM sessions WHERE project_id = :projectId")
    suspend fun deleteSessionsForProject(projectId: String): Int

    /**
     * 删除会话及其消息(单事务):messages 的 FK 级联(ON DELETE CASCADE)
     * 在 sessions 行删除时同步清掉消息,两步天然原子。
     */
    @Transaction
    suspend fun deleteSessionWithMessages(
        sessionId: String,
        messageDao: com.aeibi.design.data.messages.MessageDao
    ): Int {
        messageDao.deleteMessagesForSession(sessionId)
        return deleteSession(sessionId)
    }
}
