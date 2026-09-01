package com.aeibi.design.data.projectfiles

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectFileToolsFactoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun create_bindsWorkspaceDirectoryOfProject() = runBlocking {
        val workspace = File(tmp.root, "project-a").resolve("workspace")
        workspace.mkdirs()
        File(workspace, "index.html").writeText("<h1>hi</h1>")
        val factory = ProjectFileToolsFactory(tmp.root)

        val tools = factory.create("project-a")
        val result = tools.read("index.html")

        assertEquals("<h1>hi</h1>", (result as FileToolResult.Success).value.content)
    }
}
