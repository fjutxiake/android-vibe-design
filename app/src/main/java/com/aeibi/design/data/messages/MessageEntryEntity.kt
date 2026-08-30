package com.aeibi.design.data.messages

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.json.Json

/**
 * 会话历史条目:append-only 日志的一行。
 *
 * 存储模型对齐主流 agent 转写方案(pi 的 entries 表 / Claude Code 的 JSONL):
 * - `seq`:会话内单调递增序号,在插入事务内分配,`UNIQUE(session_id, seq)` 兜底并发写;
 *   `created_at` 仅用于展示,绝不参与排序。
 * - `type + payload`:版本化的结构化条目。`type` 区分条目种类(当前只有
 *   `message.user` / `message.assistant`,未来的工具调用、思考块等以新 type 接入);
 *   具体字段(role/status/content、未来的 provider/model/usage/stop reason)都在
 *   payload JSON 里,按 type 版本演进。
 * - `parent_id` 本期不引入,但布局不阻碍(pi 以可空列 + 索引即可加入)。
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["session_id", "seq"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = com.aeibi.design.data.sessions.SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "seq")
    val seq: Long,
    @ColumnInfo(name = "type")
    val type: MessageEntryType,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

/** 条目类型。值即存储字符串,新增种类只加枚举值。 */
enum class MessageEntryType(val value: String) {
    USER_MESSAGE("message.user"),
    ASSISTANT_MESSAGE("message.assistant");

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: String): MessageEntryType =
            byValue[value] ?: throw IllegalArgumentException("未知条目类型: $value")
    }
}

/**
 * 消息条目的 payload 结构。
 *
 * 版本策略:只在新增字段时保持向后兼容(旧 JSON 缺字段时取默认值);字段语义变化时
 * 升级 type 或引入新 type,不在旧 type 上做不兼容改动。
 */
@kotlinx.serialization.Serializable
data class MessagePayload(
    val role: MessageRole,
    val status: MessageStatus,
    val content: String,
    /** 生成该条目时使用的 provider 配置 id 与 model(请求快照,供历史溯源)。 */
    val providerConfigId: String? = null,
    val model: String? = null,
    /** 失败原因(仅 FAILED 状态填充)。 */
    val error: String? = null
)

/** 消息角色。预留扩展值(如未来的工具活动),不影响 TEXT 列的存储格式。 */
@kotlinx.serialization.Serializable
enum class MessageRole {
    USER,
    ASSISTANT
}

/** 消息完成状态:被中断或失败的回复不能呈现为已成功完成的消息。 */
@kotlinx.serialization.Serializable
enum class MessageStatus {
    /** 生成中。正常流程仅在 #23 接入后出现,启动 reconcile 会把它收敛为终态。 */
    STREAMING,

    COMPLETED,
    INTERRUPTED,
    FAILED
}

/** payload 的编解码。模块级单例,配置对未知字段宽容以便向前兼容。 */
object MessagePayloadCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: MessagePayload): String = json.encodeToString(MessagePayload.serializer(), payload)

    fun decode(raw: String): MessagePayload = json.decodeFromString(MessagePayload.serializer(), raw)
}

/** 便捷视图:实体 + 已解码 payload,供 UI 与上层直接消费。 */
data class MessageEntry(val entity: MessageEntryEntity, val payload: MessagePayload) {
    val id: String get() = entity.id
    val sessionId: String get() = entity.sessionId
    val seq: Long get() = entity.seq
    val type: MessageEntryType get() = entity.type
    val createdAt: Long get() = entity.createdAt
    val role: MessageRole get() = payload.role
    val status: MessageStatus get() = payload.status
    val content: String get() = payload.content
    val error: String? get() = payload.error
}
