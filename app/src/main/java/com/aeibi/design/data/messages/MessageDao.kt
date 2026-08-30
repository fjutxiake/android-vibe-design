package com.aeibi.design.data.messages

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query(
        """
      SELECT * FROM messages
      WHERE session_id = :sessionId
      ORDER BY seq ASC
    """
    )
    fun observeMessages(sessionId: String): Flow<List<MessageEntryEntity>>

    @Query(
        """
      SELECT * FROM messages
      WHERE session_id = :sessionId
      ORDER BY seq ASC
    """
    )
    suspend fun getMessages(sessionId: String): List<MessageEntryEntity>

    @Query(
        """
      SELECT * FROM messages
      WHERE session_id = :sessionId
      ORDER BY seq DESC
      LIMIT 1
    """
    )
    suspend fun getLatestEntry(sessionId: String): MessageEntryEntity?

    @Query("SELECT COALESCE(MAX(seq), 0) FROM messages WHERE session_id = :sessionId")
    suspend fun getMaxSeq(sessionId: String): Long

    @Query("SELECT * FROM messages WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): MessageEntryEntity?

    /**
     * 追加一条历史条目。冲突(id 或 (session_id, seq) 撞车)即失败上抛,
     * 绝不静默覆盖既有历史 —— append-only 语义的核心。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: MessageEntryEntity)

    /**
     * assistant 条目的状态转换,compare-and-set:仅当当前状态在 [allowedFrom] 内
     * 才写入 [newStatus]。返回受影响行数,0 表示源状态不匹配(转换被拒绝)。
     * 旧的 status 值通过 payload 的 SQL 函数逐条过滤,避免读改写整条记录。
     */
    @Query(
        """
      UPDATE messages
      SET payload = json_set(payload, '$.status', :newStatus)
      WHERE id = :entryId
        AND json_extract(payload, '$.status') IN (:allowedFrom)
    """
    )
    suspend fun transitionStatus(entryId: String, newStatus: String, allowedFrom: List<String>): Int

    /**
     * 生成完成时的完整收敛:content 与 status 一并写入(仍带 CAS 守卫)。
     * error 仅在转 FAILED 时写入;转 COMPLETED 时清除。
     */
    @Query(
        """
      UPDATE messages
      SET payload = json_set(
            json_set(payload, '$.content', :content, '$.status', :newStatus),
            '$.error',
            CASE WHEN :newStatus = 'FAILED' THEN :error ELSE NULL END
          )
      WHERE id = :entryId
        AND json_extract(payload, '$.status') IN (:allowedFrom)
    """
    )
    suspend fun updatePayloadAndStatus(
        entryId: String,
        content: String,
        newStatus: String,
        allowedFrom: List<String>,
        error: String?
    ): Int

    /**
     * 启动恢复:把所有非终态(STREAMING)的条目收敛为 INTERRUPTED。
     * 终态(COMPLETED/INTERRUPTED/FAILED)不受影响。返回收敛的条目数。
     */
    @Query(
        """
      UPDATE messages
      SET payload = json_set(payload, '$.status', :interruptedStatus)
      WHERE json_extract(payload, '$.status') = :streamingStatus
    """
    )
    suspend fun reconcileInterrupted(interruptedStatus: String, streamingStatus: String): Int

    /** 删除会话内全部消息;sessions 行删除时 FK 级联也会到达同一结果。 */
    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String): Int

    @Transaction
    suspend fun appendEntryWithSessionTouch(
        entry: MessageEntryEntity,
        sessionDao: com.aeibi.design.data.sessions.SessionDao,
        sessionUpdatedAt: Long
    ) {
        // 并发追加时 MAX(seq)+1 可能撞 UNIQUE(session_id, seq),SQLite 的
        // BEGIN DEFERRED 会让竞争方在提交时 SQLITE_BUSY/约束失败。这里重试
        // 指数退避,仍失败则如实上抛(append-only:绝不降级为覆盖)。
        var attempt = 0
        while (true) {
            try {
                val nextSeq = getMaxSeq(entry.sessionId) + 1
                insertEntry(entry.copy(seq = nextSeq))
                sessionDao.touchSession(entry.sessionId, sessionUpdatedAt)
                return
            } catch (e: android.database.SQLException) {
                attempt++
                if (attempt > 4) throw e
                kotlinx.coroutines.delay(1L shl attempt)
            }
        }
    }
}
