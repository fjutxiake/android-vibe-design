package com.aeibi.design.ai.tools

import com.aeibi.design.data.projectfiles.ProjectFileTools
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
        tools = WorkspaceTools(ProjectFileTools(workspace.toFile()))
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
    fun exposesRemainingProjectFileOperations() = runTest {
        assertEquals("Created directory assets", tools.createDirectory("assets"))
        assertEquals("Created assets/logo.txt", tools.createFile("assets/logo.txt", "logo\nmark"))

        assertEquals("assets/", tools.listDirectory("."))
        assertEquals("assets/logo.txt", tools.findFiles("*.txt", "assets"))
        assertEquals("assets/logo.txt:1: logo", tools.searchText("logo", "assets"))
        assertEquals("mark", tools.readFileRange("assets/logo.txt", 2, 1))

        assertEquals("Moved assets/logo.txt to assets/icon.txt", tools.movePath("assets/logo.txt", "assets/icon.txt"))
        assertEquals("Deleted assets/icon.txt", tools.deletePath("assets/icon.txt", recursive = false))
        assertEquals("Deleted assets", tools.deletePath("assets", recursive = true))
    }

    @Test
    fun rejectsPathsOutsideWorkspace() = runTest {
        val output = tools.writeFile("../outside.txt", "blocked")
        assertTrue(output.startsWith("ERROR [PATH_ESCAPE]:"))
        assertFalse(workspace.parent.resolve("outside.txt").toFile().exists())
    }

    @Test
    fun replaceRequiresExactlyOneMatchAndLeavesFileUnchanged() = runTest {
        tools.writeFile("styles.css", "red red")

        val missingOutput = tools.replaceText("styles.css", "blue", "green")
        assertTrue(missingOutput.startsWith("ERROR [CONFLICT]:"))
        assertEquals("red red", tools.readFile("styles.css"))

        val duplicateOutput = tools.replaceText("styles.css", "red", "green")
        assertTrue(duplicateOutput.startsWith("ERROR [CONFLICT]:"))
        assertEquals("red red", tools.readFile("styles.css"))
    }
}
