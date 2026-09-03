package com.aeibi.design.ai.tools

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintToolsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun workspace(): File = temporaryFolder.newFolder()

    @Test
    fun healthyHtmlAndJs_passes() = runBlocking {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """
            <html><head><link rel="stylesheet" href="styles.css"></head>
            <body><script src="app.js"></script></body></html>
            """.trimIndent()
        )
        File(ws, "styles.css").writeText("body {}")
        File(ws, "app.js").writeText("console.log('ok');")

        val output = LintTools(ws).lintFiles()

        assertTrue("应通过: $output", output.startsWith("Lint OK"))
    }

    @Test
    fun brokenLocalReference_reported() = runBlocking {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """<html><body><img src="images/missing.png"><script src="app.js"></script></body></html>"""
        )
        File(ws, "app.js").writeText("console.log('ok');")

        val output = LintTools(ws).lintFiles()

        assertTrue("应报告断链: $output", output.contains("missing.png"))
    }

    @Test
    fun jsSyntaxError_reportedWithLine() = runBlocking {
        val ws = workspace()
        File(ws, "index.html").writeText("<html><body></body></html>")
        File(ws, "app.js").writeText("const x = ;\nconsole.log(x);")

        val output = LintTools(ws).lintFiles()

        assertTrue("应报告 JS 语法错误: $output", output.contains("app.js:1"))
        assertTrue(output.contains("JS:"))
    }

    @Test
    fun externalRefsIgnored() = runBlocking {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """<html><head><link rel="stylesheet" href="https://cdn.example.com/x.css"></head>
            <body><script src="//cdn.example.com/y.js"></script></body></html>"""
        )

        val output = LintTools(ws).lintFiles()

        assertTrue("外链不应误报: $output", output.startsWith("Lint OK"))
    }

    @Test
    fun pathOutsideWorkspace_rejected() = runBlocking {
        val ws = workspace()
        File(ws, "index.html").writeText("<html/>")

        val escaped = try {
            LintTools(ws).lintFiles(path = "../outside")
            false
        } catch (expected: IllegalStateException) {
            true
        }
        assertTrue("逃逸路径应被拒绝", escaped)
    }
}
