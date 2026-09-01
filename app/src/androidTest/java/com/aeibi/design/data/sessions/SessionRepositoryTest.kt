package com.aeibi.design.data.sessions

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun entriesAreOrderedAndDeletedWithTheirSession() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = SessionRepository(database.sessionDao())
            repository.saveSession(
                SessionEntity(
                    id = "session",
                    projectId = "project",
                    title = "Title",
                    createdAt = 1,
                    updatedAt = 1
                )
            )
            repository.appendMessage(
                "session",
                "turn",
                MessageOrigin.USER,
                Message.User("First", RequestMetaInfo.Empty)
            )
            repository.appendMessage(
                "session",
                "turn",
                MessageOrigin.USER,
                Message.User("Second", RequestMetaInfo.Empty)
            )

            assertEquals(
                listOf("First", "Second"),
                repository.loadModelMessages("session").map { it.textContent() }
            )

            repository.deleteSession("session")
            assertEquals(emptyList<SessionEntryEntity>(), database.sessionDao().getEntries("session"))
        } finally {
            database.close()
        }
    }
}
