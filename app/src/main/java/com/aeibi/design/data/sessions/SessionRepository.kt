package com.aeibi.design.data.sessions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
  private val sessionDao: SessionDao,
) {

  fun observeSessions(projectId: String): Flow<List<SessionEntity>> =
    sessionDao.observeSessions(projectId)

  suspend fun getSession(sessionId: String): SessionEntity? =
    sessionDao.getSession(sessionId)

  suspend fun saveSession(session: SessionEntity) {
    sessionDao.upsertSession(session)
  }

  suspend fun renameSession(
    sessionId: String,
    title: String,
    updatedAt: Long,
  ): Boolean = sessionDao.renameSession(sessionId, title, updatedAt) > 0

  suspend fun touchSession(
    sessionId: String,
    updatedAt: Long,
  ): Boolean = sessionDao.touchSession(sessionId, updatedAt) > 0

  suspend fun deleteSession(sessionId: String): Boolean =
    sessionDao.deleteSession(sessionId) > 0

  suspend fun deleteSessionsForProject(projectId: String): Int =
    sessionDao.deleteSessionsForProject(projectId)
}
