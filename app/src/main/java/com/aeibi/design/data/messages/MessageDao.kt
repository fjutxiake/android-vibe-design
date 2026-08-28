package com.aeibi.design.data.messages

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query(
        """
      SELECT * FROM messages
      WHERE session_id = :sessionId
      ORDER BY created_at ASC
    """
    )
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query(
        """
      SELECT * FROM messages
      WHERE session_id = :sessionId
      ORDER BY created_at ASC
    """
    )
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String): Int

    @Query(
        """
      DELETE FROM messages
      WHERE session_id IN (SELECT id FROM sessions WHERE project_id = :projectId)
    """
    )
    suspend fun deleteMessagesForProject(projectId: String): Int
}
