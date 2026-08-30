package com.aeibi.design.data.messages

import com.aeibi.design.data.sessions.SessionDao
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 会话历史的唯一写入入口。
 *
 * 语义(对照 agent 转写惯例):
 * - append-only:[appendMessage] 在事务内分配 seq 并插入,冲突即失败,不覆盖历史。
 * - 显式状态转换:[updateAssistantStatus] 只放行合法的转移(STREAMING → 终态),
 *   compare-and-set 防止迟到的写入者改写已完成的历史。
 * - 原子性:追加与 touch 会话在同一 Room 事务内。
 */
@Singleton
class MessageRepository @Inject constructor(private val messageDao: MessageDao, private val sessionDao: SessionDao) {

    /** 底层 DAO,仅供跨表事务组合使用(如删会话连带消息),不对外扩散。 */
    val dao: MessageDao get() = messageDao

    /** 观察指定会话的完整条目流(payload 已解码),Room 数据变化自动推送。 */
    fun observeMessages(sessionId: String): Flow<List<MessageEntry>> =
        messageDao.observeMessages(sessionId).map { entries -> entries.map(::decode) }

    /** 读取指定会话的完整条目列表,用于后续把历史回合提供给 AI。 */
    suspend fun getMessages(sessionId: String): List<MessageEntry> = messageDao.getMessages(sessionId).map(::decode)

    /** 追加一条消息;seq 与会话 updated_at 的更新在同一事务内完成。 */
    suspend fun appendMessage(
        sessionId: String,
        type: MessageEntryType,
        payload: MessagePayload,
        id: String,
        createdAt: Long
    ): MessageEntry {
        val entity = MessageEntryEntity(
            id = id,
            sessionId = sessionId,
            seq = 0L, // 占位,真实值由 DAO 在事务内分配
            type = type,
            payload = MessagePayloadCodec.encode(payload),
            createdAt = createdAt
        )
        messageDao.appendEntryWithSessionTouch(entity, sessionDao, createdAt)
        // 按插入的 id 精确读回(而非 MAX(seq)),并发追加时才不会读到别人的条目。
        val stored = messageDao.getEntryById(id) ?: error("条目插入后应能读回: $id")
        return decode(stored)
    }

    /**
     * assistant 条目的状态转换。返回是否成功:源状态不在 [allowedFrom] 内时
     * 拒绝转换(历史未被改写),调用方自行决定重读或放弃。
     */
    suspend fun updateAssistantStatus(
        entryId: String,
        newStatus: MessageStatus,
        allowedFrom: List<MessageStatus>
    ): Boolean = messageDao.transitionStatus(
        entryId = entryId,
        newStatus = newStatus.name,
        allowedFrom = allowedFrom.map { it.name }
    ) > 0

    /**
     * 生成完成时的完整收敛:content 与 status 一并写入(仍带 CAS 守卫,
     * 仅 STREAMING 可转终态)。error 仅在转 FAILED 时落库;转 COMPLETED 清除。
     * 返回是否成功:源状态不符时历史未被改写。
     */
    suspend fun updatePayloadAndStatus(
        entryId: String,
        content: String,
        newStatus: MessageStatus,
        allowedFrom: List<MessageStatus>,
        error: String? = null
    ): Boolean = messageDao.updatePayloadAndStatus(
        entryId = entryId,
        content = content,
        newStatus = newStatus.name,
        allowedFrom = allowedFrom.map { it.name },
        error = error
    ) > 0

    /**
     * 启动恢复:应用在 assistant 生成中途退出后,遗留的 STREAMING 条目在下次
     * 启动时确定性地收敛为 INTERRUPTED。产生源在 #23 接入,不变式现在就位。
     */
    suspend fun reconcileInterruptedEntries(): Int = messageDao.reconcileInterrupted(
        interruptedStatus = MessageStatus.INTERRUPTED.name,
        streamingStatus = MessageStatus.STREAMING.name
    )

    /** 删除会话内消息。常规路径由 FK 级联兜底,此方法用于显式清理。 */
    suspend fun deleteMessagesForSession(sessionId: String): Int = messageDao.deleteMessagesForSession(sessionId)

    private fun decode(entity: MessageEntryEntity): MessageEntry =
        MessageEntry(entity = entity, payload = MessagePayloadCodec.decode(entity.payload))
}
