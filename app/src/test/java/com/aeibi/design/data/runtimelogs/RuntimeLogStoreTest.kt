package com.aeibi.design.data.runtimelogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogStoreTest {

    private val store = RuntimeLogStore()

    private fun entry(level: String = "ERROR", message: String = "msg", source: String = "app.js:1") =
        RuntimeLogEntry(level = level, message = message, source = source, timestamp = 0L)

    @Test
    fun recordAndSnapshot_keepsOrder() {
        store.record(PROJECT_A, entry(message = "first"))
        store.record(PROJECT_A, entry(message = "second"))

        val entries = store.snapshot(PROJECT_A)
        assertEquals(2, entries.size)
        assertEquals("first", entries[0].message)
        assertEquals("second", entries[1].message)
    }

    @Test
    fun record_capsAtMaxEntries() {
        repeat(250) { store.record(PROJECT_A, entry(message = "m$it")) }

        val entries = store.snapshot(PROJECT_A)
        assertEquals(200, entries.size)
        // 环形缓冲保留最新
        assertEquals("m50", entries.first().message)
        assertEquals("m249", entries.last().message)
    }

    @Test
    fun clear_emptiesSnapshot() {
        store.record(PROJECT_A, entry())
        store.clear(PROJECT_A)

        assertTrue(store.snapshot(PROJECT_A).isEmpty())
    }

    @Test
    fun snapshot_isIsolatedByProject() {
        store.record(PROJECT_A, entry(message = "project-a"))
        store.record(PROJECT_B, entry(message = "project-b"))

        assertEquals(listOf("project-a"), store.snapshot(PROJECT_A).map { it.message })
        assertEquals(listOf("project-b"), store.snapshot(PROJECT_B).map { it.message })
    }

    private companion object {
        const val PROJECT_A = "project-a"
        const val PROJECT_B = "project-b"
    }
}
