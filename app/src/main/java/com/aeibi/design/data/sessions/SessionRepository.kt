package com.aeibi.design.data.sessions

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SessionRepository @Inject constructor(private val sessionDao: SessionDao) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun observeSessions(projectId: String): Flow<List<SessionEntity>> = sessionDao.observeSessions(projectId)

    suspend fun getSession(sessionId: String): SessionEntity? = sessionDao.getSession(sessionId)

    suspend fun saveSession(session: SessionEntity) {
        sessionDao.upsertSession(session)
    }

    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Boolean =
        sessionDao.renameSession(sessionId, title, updatedAt) > 0

    suspend fun touchSession(sessionId: String, updatedAt: Long): Boolean =
        sessionDao.touchSession(sessionId, updatedAt) > 0

    fun observeEntries(sessionId: String): Flow<List<SessionEntryEntity>> = sessionDao.observeEntries(sessionId)

    suspend fun appendMessage(sessionId: String, turnId: String, origin: MessageOrigin, message: Message) {
        appendEntry(
            sessionId = sessionId,
            turnId = turnId,
            type = SessionEntryType.MESSAGE,
            payload = json.encodeToString(MessageEntryPayload(origin, message.withoutRawResponse()))
        )
    }

    suspend fun replaceContext(sessionId: String, turnId: String?, messages: List<Message>) {
        appendEntry(
            sessionId = sessionId,
            turnId = turnId,
            type = SessionEntryType.CONTEXT_REPLACED,
            payload = json.encodeToString(ContextReplacedPayload(messages.map { it.withoutRawResponse() }))
        )
    }

    suspend fun finishTurn(
        sessionId: String,
        turnId: String,
        status: TurnStatus,
        failure: AgentFailure? = null,
        partialResponse: String? = null,
        partialReasoning: String? = null
    ) {
        withContext(NonCancellable) {
            appendEntry(
                sessionId = sessionId,
                turnId = turnId,
                type = SessionEntryType.TURN_FINISHED,
                payload = json.encodeToString(TurnFinishedPayload(status, failure, partialResponse, partialReasoning))
            )
        }
    }

    suspend fun loadModelMessages(sessionId: String): List<Message> {
        var messages = mutableListOf<Message>()
        sessionDao.getEntries(sessionId).forEach { entry ->
            when (SessionEntryType.valueOf(entry.type)) {
                SessionEntryType.MESSAGE -> messages += decodeMessage(entry).message
                SessionEntryType.CONTEXT_REPLACED -> {
                    messages = decodeContextReplacement(entry).messages.toMutableList()
                }
                SessionEntryType.TURN_FINISHED -> {
                    if (decodeTurnFinished(entry).status == TurnStatus.CANCELLED) {
                        messages += Message.User(INTERRUPTED_TURN_CONTEXT, RequestMetaInfo.Empty)
                    }
                }
            }
        }
        return messages
    }

    suspend fun repairInterruptedToolCalls(sessionId: String) {
        val entries = sessionDao.getEntries(sessionId)
        val calls = mutableListOf<Pair<String?, MessagePart.Tool.Call>>()
        val results = mutableSetOf<String?>()
        entries.forEach { entry ->
            if (SessionEntryType.valueOf(entry.type) != SessionEntryType.MESSAGE) return@forEach
            when (val message = decodeMessage(entry).message) {
                is Message.Assistant -> message.parts.filterIsInstance<MessagePart.Tool.Call>().forEach {
                    calls += entry.turnId to it
                }
                is Message.User -> message.parts.filterIsInstance<MessagePart.Tool.Result>().forEach {
                    results += it.id
                }
                is Message.System -> Unit
            }
        }
        calls.filter { (_, call) -> call.id !in results }.forEach { (turnId, call) ->
            appendMessage(
                sessionId = sessionId,
                turnId = requireNotNull(turnId),
                origin = MessageOrigin.TOOL,
                message = Message.User(
                    MessagePart.Tool.Result(
                        id = call.id,
                        tool = call.tool,
                        output = UNKNOWN_TOOL_OUTCOME,
                        isError = true
                    ),
                    ai.koog.prompt.message.RequestMetaInfo.Empty
                )
            )
        }
    }

    suspend fun deleteSession(sessionId: String): Boolean = sessionDao.deleteSession(sessionId) > 0

    suspend fun deleteSessionsForProject(projectId: String): Int = sessionDao.deleteSessionsForProject(projectId)

    fun decodeMessage(entry: SessionEntryEntity): MessageEntryPayload = json.decodeFromString(entry.payload)

    fun decodeTurnFinished(entry: SessionEntryEntity): TurnFinishedPayload = json.decodeFromString(entry.payload)

    private suspend fun appendEntry(sessionId: String, turnId: String?, type: SessionEntryType, payload: String) {
        sessionDao.appendEntry(
            SessionEntryEntity(
                sessionId = sessionId,
                turnId = turnId,
                type = type.name,
                payload = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun decodeContextReplacement(entry: SessionEntryEntity): ContextReplacedPayload =
        json.decodeFromString(entry.payload)

    private fun Message.withoutRawResponse(): Message = when (this) {
        is Message.Assistant -> copy(rawResponse = null)
        else -> this
    }

    private companion object {
        const val INTERRUPTED_TURN_CONTEXT =
            "The previous turn was interrupted on purpose. Any interrupted tool calls may have partially executed. Inspect the workspace before continuing."
        const val UNKNOWN_TOOL_OUTCOME =
            "The tool execution was interrupted and its outcome is unknown. Inspect the workspace before retrying it."
    }
}
