package com.aeibi.design.data.sessions

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemorySessionDao : SessionDao {
    private val sessions = mutableMapOf<String, SessionEntity>()
    private val entries = MutableStateFlow<List<SessionEntryEntity>>(emptyList())
    private var nextEntryId = 1L

    override fun observeSessions(projectId: String): Flow<List<SessionEntity>> = entries.map {
        sessions.values
            .filter { session -> session.projectId == projectId }
            .sortedByDescending(SessionEntity::updatedAt)
    }

    override suspend fun getSession(sessionId: String): SessionEntity? = sessions[sessionId]

    override suspend fun upsertSession(session: SessionEntity) {
        sessions[session.id] = session
    }

    override suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Int {
        val session = sessions[sessionId] ?: return 0
        sessions[sessionId] = session.copy(title = title, updatedAt = updatedAt)
        return 1
    }

    override suspend fun touchSession(sessionId: String, updatedAt: Long): Int {
        val session = sessions[sessionId] ?: return 0
        sessions[sessionId] = session.copy(updatedAt = updatedAt)
        return 1
    }

    override fun observeEntries(sessionId: String): Flow<List<SessionEntryEntity>> =
        entries.map { values -> values.filter { it.sessionId == sessionId } }

    override suspend fun getEntries(sessionId: String): List<SessionEntryEntity> =
        entries.value.filter { it.sessionId == sessionId }

    override suspend fun insertEntry(entry: SessionEntryEntity) {
        currentCoroutineContext().ensureActive()
        entries.value += entry.copy(id = nextEntryId++)
    }

    override suspend fun deleteSession(sessionId: String): Int {
        if (sessions.remove(sessionId) == null) return 0
        entries.value = entries.value.filterNot { it.sessionId == sessionId }
        return 1
    }

    override suspend fun deleteSessionsForProject(projectId: String): Int {
        val ids = sessions.values.filter { it.projectId == projectId }.map(SessionEntity::id)
        for (id in ids) deleteSession(id)
        return ids.size
    }
}
