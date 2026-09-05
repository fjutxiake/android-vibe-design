package com.aeibi.design.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatErrorReportTest {
    @Test
    fun markerMessageParsesToCollapsibleReport() {
        val text = """
            [error-report] Preview load failure — 2 error(s) on index.html at 16:45

            | # | Time | Error | URL |
            |---|------|-------|-----|
            | 1 | 16:45:01 | HTTP 404 | http://localhost:37193/ |
            | 2 | 16:45:11 | ERR_FILE_NOT_FOUND | http://localhost:37193/assets/app.js |
        """.trimIndent()

        val report = text.toErrorReport("42")

        assertEquals(ChatTimelineItem.ErrorReport::class, report?.javaClass?.kotlin)
        val item = requireNotNull(report)
        assertEquals("42", item.id)
        assertEquals("Preview load failure — 2 error(s) on index.html at 16:45", item.summary)
        assertEquals(
            listOf(
                "| # | Time | Error | URL |",
                "|---|------|-------|-----|",
                "| 1 | 16:45:01 | HTTP 404 | http://localhost:37193/ |",
                "| 2 | 16:45:11 | ERR_FILE_NOT_FOUND | http://localhost:37193/assets/app.js |"
            ),
            item.body.lines()
        )
    }

    @Test
    fun plainMessageIsNotAReport() {
        assertNull("Build me a landing page".toErrorReport("1"))
    }

    @Test
    fun markerOnlyMessageFallsBackToEmptyBody() {
        val report = "[error-report] Preview load failure — 1 error(s)".toErrorReport("2")

        assertEquals("Preview load failure — 1 error(s)", requireNotNull(report).summary)
        assertEquals("", report.body)
    }
}
