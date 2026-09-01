package com.aeibi.design.ai.tools

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceToolsTest {
    private lateinit var workspace: java.nio.file.Path
    private lateinit var tools: WorkspaceTools

    @Before
    fun setUp() {
        workspace = Files.createTempDirectory("workspace-tools-test")
        tools = WorkspaceTools(workspace.toFile())
    }

    @After
    fun tearDown() {
        workspace.toFile().deleteRecursively()
    }

    @Test
    fun listsReadsWritesAndReplacesFiles() = runTest {
        assertEquals("Updated nested/page.html", tools.writeFile("nested/page.html", "old"))
        tools.writeFile("index.html", "home")

        assertEquals("index.html\nnested/page.html", tools.listFiles())
        assertEquals("old", tools.readFile("nested/page.html"))
        assertEquals("Updated nested/page.html", tools.replaceText("nested/page.html", "old", "new"))
        assertEquals("new", tools.readFile("nested/page.html"))
    }

    @Test
    fun rejectsPathsOutsideWorkspace() = runTest {
        val error = runCatching { tools.writeFile("../outside.txt", "blocked") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(workspace.parent.resolve("outside.txt").toFile().exists())
    }

    @Test
    fun replaceRequiresExactlyOneMatchAndLeavesFileUnchanged() = runTest {
        tools.writeFile("styles.css", "red red")

        val missingError = runCatching {
            tools.replaceText("styles.css", "blue", "green")
        }.exceptionOrNull()
        assertTrue(missingError is IllegalArgumentException)
        assertEquals("red red", tools.readFile("styles.css"))

        val duplicateError = runCatching {
            tools.replaceText("styles.css", "red", "green")
        }.exceptionOrNull()
        assertTrue(duplicateError is IllegalArgumentException)
        assertEquals("red red", tools.readFile("styles.css"))
    }
}
