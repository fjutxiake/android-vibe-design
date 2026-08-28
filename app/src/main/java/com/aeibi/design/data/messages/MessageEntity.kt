package com.aeibi.design.data.messages

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** 消息角色。预留扩展值(如未来的工具活动),不影响 TEXT 列的存储格式。 */
enum class MessageRole {
    USER,
    ASSISTANT
}

/** 消息完成状态:被中断或失败的回复不能呈现为已成功完成的消息。 */
enum class MessageStatus {
    COMPLETED,
    INTERRUPTED,
    FAILED
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["session_id", "created_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "role")
    val role: MessageRole,
    @ColumnInfo(name = "status")
    val status: MessageStatus,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
