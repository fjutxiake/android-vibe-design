package com.aeibi.design.ai.tools

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReloadToolTest {

    @Test
    fun invokingToolRequestsReloadAndReportsBack() = runTest {
        var requests = 0
        val tool = PreviewReloadTool { requests++ }

        val result = tool.reloadPreview()

        assertEquals("Reload callback fires exactly once", 1, requests)
        assertTrue(result.contains("reload requested"))
        assertTrue(result.contains("read_runtime_logs"))
    }

    @Test
    fun everyInvocationRequestsANewReload() = runTest {
        var requests = 0
        val tool = PreviewReloadTool { requests++ }

        tool.reloadPreview()
        tool.reloadPreview()
        tool.reloadPreview()

        assertEquals(3, requests)
    }
}
