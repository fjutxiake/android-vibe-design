package com.aeibi.design.data.verification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectVerifierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val verifier = ProjectVerifier()

    private fun workspace(): File = temporaryFolder.newFolder()

    @Test
    fun healthyWorkspace_passes() {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """
            <html><head><link rel="stylesheet" href="styles.css"></head>
            <body><script src="app.js"></script><img src="img/logo.png"></body></html>
            """.trimIndent()
        )
        File(ws, "styles.css").writeText("body{}")
        File(ws, "app.js").writeText("console.log('ok')")
        File(ws, "img/logo.png").apply { parentFile.mkdirs() }.writeText("png")

        val report = verifier.verify(ws)

        assertTrue("应通过: ${report.summary()}", report.passed)
        assertTrue(report.results.any { it.id == "entry-exists" && it.severity == CheckSeverity.OK })
        assertTrue(report.results.any { it.id == "local-refs" && it.severity == CheckSeverity.OK })
    }

    @Test
    fun missingEntry_fails() {
        val ws = workspace()

        val report = verifier.verify(ws)

        assertFalse(report.passed)
        val entry = report.results.first { it.id == "entry-exists" }
        assertEquals(CheckSeverity.ERROR, entry.severity)
    }

    @Test
    fun configEntryOverridesDefault() {
        val ws = workspace()
        File(ws, "vibe.config.json").writeText("""{"preview":{"entry":"pages/home.html"}}""")
        File(ws, "pages/home.html").apply { parentFile.mkdirs() }.writeText("<html/>")

        val report = verifier.verify(ws)

        assertTrue(report.passed)
        assertTrue(report.results.any { it.id == "entry-exists" && it.message.contains("pages/home.html") })
    }

    @Test
    fun brokenLocalRefs_fail() {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """
            <html><body><script src="app.js"></script>
            <img src="images/missing.png"></body></html>
            """.trimIndent()
        )
        File(ws, "app.js").writeText("ok")

        val report = verifier.verify(ws)

        assertFalse(report.passed)
        val refs = report.results.first { it.id == "local-refs" }
        assertEquals(CheckSeverity.ERROR, refs.severity)
        assertTrue(refs.message.contains("images/missing.png"))
    }

    @Test
    fun externalRefsAreIgnored() {
        val ws = workspace()
        File(ws, "index.html").writeText(
            """
            <html><head><link rel="stylesheet" href="https://cdn.example.com/x.css"></head>
            <body><script src="//cdn.example.com/y.js"></script>
            <a href="#section">锚点</a><img src="data:image/png;base64,xx"></body></html>
            """.trimIndent()
        )

        val report = verifier.verify(ws)

        assertTrue(report.passed)
    }

    @Test
    fun malformedConfig_failsWithConfigError() {
        val ws = workspace()
        File(ws, "vibe.config.json").writeText("{malformed")
        File(ws, "index.html").writeText("<html/>")

        val report = verifier.verify(ws)

        assertTrue(report.results.any { it.id == "config-well-formed" && it.severity == CheckSeverity.ERROR })
        assertTrue(report.results.any { it.id == "entry-exists" && it.severity == CheckSeverity.OK })
    }

    @Test
    fun noHtmlFiles_warnsAndPasses() {
        // entry 指向非 HTML（如纯配置工作区）→ 无 HTML 可查 → 引用检查 WARNING 而非失败
        val ws = workspace()
        File(ws, "vibe.config.json").writeText("""{"preview":{"entry":"config.json"}}""")
        File(ws, "config.json").writeText("""{"theme":"dark"}""")

        val report = verifier.verify(ws)

        assertTrue(report.passed)
        assertTrue(report.results.any { it.id == "entry-exists" && it.severity == CheckSeverity.OK })
        assertTrue(report.results.any { it.id == "local-refs" && it.severity == CheckSeverity.WARNING })
    }
}
