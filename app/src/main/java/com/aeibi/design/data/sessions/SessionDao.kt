package com.aeibi.design.data.sessions

import androidx.room3.Dao
import androidx.room3.Insert
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

    @Query("SELECT * FROM session_entries WHERE session_id = :sessionId ORDER BY id ASC")
    fun observeEntries(sessionId: String): Flow<List<SessionEntryEntity>>

    @Query("SELECT * FROM session_entries WHERE session_id = :sessionId ORDER BY id ASC")
    suspend fun getEntries(sessionId: String): List<SessionEntryEntity>

    @Insert
    suspend fun insertEntry(entry: SessionEntryEntity)

    @Transaction
    suspend fun appendEntry(entry: SessionEntryEntity) {
        insertEntry(entry)
        touchSession(entry.sessionId, entry.createdAt)
    }

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    @Query("DELETE FROM sessions WHERE project_id = :projectId")
    suspend fun deleteSessionsForProject(projectId: String): Int
}
