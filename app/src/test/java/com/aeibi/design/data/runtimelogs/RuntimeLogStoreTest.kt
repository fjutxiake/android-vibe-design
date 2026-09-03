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
        store.record(entry(message = "first"))
        store.record(entry(message = "second"))

        val entries = store.snapshot()
        assertEquals(2, entries.size)
        assertEquals("first", entries[0].message)
        assertEquals("second", entries[1].message)
    }

    @Test
    fun record_capsAtMaxEntries() {
        repeat(250) { store.record(entry(message = "m$it")) }

        val entries = store.snapshot()
        assertEquals(200, entries.size)
        // 环形缓冲保留最新
        assertEquals("m50", entries.first().message)
        assertEquals("m249", entries.last().message)
    }

    @Test
    fun clear_emptiesSnapshot() {
        store.record(entry())
        store.clear()

        assertTrue(store.snapshot().isEmpty())
    }
}
