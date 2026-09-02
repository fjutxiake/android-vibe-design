package com.aeibi.design.data.sessions

import ai.koog.prompt.message.Message
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "session_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id", "id"])]
)
data class SessionEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "turn_id")
    val turnId: String?,
    val type: String,
    val payload: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

enum class SessionEntryType {
    MESSAGE,
    CONTEXT_REPLACED,
    TURN_FINISHED
}

@Serializable
data class MessageEntryPayload(val origin: MessageOrigin, val message: Message)

@Serializable
data class ContextReplacedPayload(val messages: List<Message>)

@Serializable
data class TurnFinishedPayload(
    val status: TurnStatus,
    val failure: AgentFailure?,
    val partialResponse: String? = null,
    val partialReasoning: String? = null
)

@Serializable
data class AgentFailure(val message: String, val code: String)

@Serializable
enum class MessageOrigin {
    USER,
    ASSISTANT,
    TOOL
}

@Serializable
enum class TurnStatus {
    COMPLETE,
    FAILED,
    CANCELLED
}
