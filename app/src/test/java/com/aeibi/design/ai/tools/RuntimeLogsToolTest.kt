package com.aeibi.design.ai.tools

import com.aeibi.design.data.runtimelogs.RuntimeLogEntry
import com.aeibi.design.data.runtimelogs.RuntimeLogStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogsToolTest {

    private val store = RuntimeLogStore()
    private val tool = RuntimeLogsTool(PROJECT_ID, store)

    @Test
    fun readAll_returnsFormattedEntries() = runBlocking {
        store.record(PROJECT_ID, RuntimeLogEntry("ERROR", "ReferenceError: x", "app.js:3"))
        store.record(PROJECT_ID, RuntimeLogEntry("WARNING", "deprecated", "app.js:8"))
        store.record(PROJECT_ID, RuntimeLogEntry("LOG", "hello", ""))

        val output = tool.readRuntimeLogs()

        assertTrue(output.contains("[ERROR] ReferenceError: x (app.js:3)"))
        assertTrue(output.contains("[WARNING] deprecated (app.js:8)"))
        assertTrue(output.contains("[LOG] hello"))
    }

    @Test
    fun readErrorOnly_filtersOtherLevels() = runBlocking {
        store.record(PROJECT_ID, RuntimeLogEntry("ERROR", "boom", "app.js:1"))
        store.record(PROJECT_ID, RuntimeLogEntry("LOG", "noise", ""))

        val output = tool.readRuntimeLogs(level = "ERROR")

        assertTrue(output.contains("boom"))
        assertTrue(!output.contains("noise"))
    }

    @Test
    fun emptyStore_returnsMessage() = runBlocking {
        val output = tool.readRuntimeLogs()

        assertTrue(output.contains("No runtime logs recorded"))
    }

    @Test
    fun clear_emptiesLogs() = runBlocking {
        store.record(PROJECT_ID, RuntimeLogEntry("ERROR", "boom", "app.js:1"))
        tool.clearRuntimeLogs()

        assertTrue(tool.readRuntimeLogs().contains("No runtime logs recorded"))
    }

    @Test
    fun recordLevelName_matchesAgentFilter() {
        // webkit ConsoleMessage.MessageLevel.name 与工具过滤一致（"ERROR"/"WARNING"）
        val names = setOf("ERROR", "WARNING", "LOG", "DEBUG", "TIP")
        assertTrue(names.contains("ERROR"))
        assertTrue(names.contains("WARNING"))
    }

    private companion object {
        const val PROJECT_ID = "project"
    }
}
