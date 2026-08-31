package com.aeibi.design.data.projects

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repository(root: File, assets: AssetManager = context.assets) = ProjectRepository(
        root,
        context.contentResolver,
        assets,
        UnconfinedTestDispatcher()
    )

    @Test
    fun workspaceDirectoryReturnsProjectWorkspace() {
        val root = tmp.newFolder()

        val workspace = repository(root).workspaceDirectory("project-id")

        assertEquals(File(File(root, "project-id"), "workspace"), workspace)
    }

    @Test
    fun createProject_writesJsonAndListsProject() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)

        val created = repo.createProject("周末去哪", "短途路线", null)

        assertTrue(File(root, created.id).isDirectory)
        assertTrue(File(File(root, created.id), "workspace").isDirectory)
        val metadataFile = File(File(root, created.id), "project.json")
        assertTrue(metadataFile.exists())
        assertFalse(metadataFile.readText().contains("\"id\""))
        assertFalse(metadataFile.readText().contains("\"icon\""))
        assertEquals("周末去哪", created.name)
        assertFalse(created.isInitialized)
        repo.refresh()
        assertEquals(listOf(created), repo.projects.value)
    }

    @Test
    fun listProjects_skipsCorruptedAndSortsByUpdatedAtDesc() = runTest {
        val root = tmp.newFolder()
        val older = File(root, "a").apply { mkdirs() }
        File(older, "project.json").writeText(
            """{"name":"旧","description":"","createdAt":1,"updatedAt":100}"""
        )
        val newer = File(root, "b").apply { mkdirs() }
        File(newer, "project.json").writeText(
            """{"name":"新","description":"","createdAt":1,"updatedAt":200}"""
        )
        val corrupt = File(root, "c").apply { mkdirs() }
        File(corrupt, "project.json").writeText("{not-json")

        val repo = repository(root)
        repo.refresh()

        assertEquals(listOf("b", "a"), repo.projects.value.map { it.id })
        assertTrue(repo.projects.value.all { it.isInitialized })
    }

    @Test
    fun markInitialized_persistsState() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val created = repo.createProject("New project", "", null)
        val workspace = File(File(root, created.id), "workspace")
        File(workspace, "unfinished.txt").writeText("unfinished")

        repo.markInitialized(created.id)

        assertTrue(workspace.listFiles().orEmpty().isEmpty())
        assertTrue(repo.getProject(created.id)?.isInitialized == true)
        assertTrue(repository(root).getProject(created.id)?.isInitialized == true)
    }

    @Test
    fun initializeFromTemplate_copiesWorkspaceAndPersistsState() = runTest {
        val root = tmp.newFolder()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val repo = repository(root, testAssets)
        val created = repo.createProject("Template project", "", null)

        repo.initializeFromTemplate(created.id, "project-template/workspace")

        val workspace = File(File(root, created.id), "workspace")
        assertEquals("template", File(workspace, "index.html").readText().trimEnd())
        assertEquals("console.log('template')", File(workspace, "scripts/app.js").readText().trimEnd())
        assertFalse(File(File(root, created.id), "workspace.pending").exists())
        assertTrue(repo.getProject(created.id)?.isInitialized == true)
    }

    @Test
    fun initializeFromTemplate_whenCopyFails_keepsProjectUninitialized() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val created = repo.createProject("Template project", "", null)

        val error = runCatching {
            repo.initializeFromTemplate(created.id, "workspace-templates/missing/workspace")
        }.exceptionOrNull()

        val projectDir = File(root, created.id)
        assertTrue(error is IOException)
        assertTrue(File(projectDir, "workspace").isDirectory)
        assertTrue(File(projectDir, "workspace").listFiles().orEmpty().isEmpty())
        assertFalse(File(projectDir, "workspace.pending").exists())
        assertFalse(repo.getProject(created.id)?.isInitialized == true)
    }

    @Test
    fun updateProject_persistsChangesAndBumpsUpdatedAt() = runTest {
        val repo = repository(tmp.newFolder())
        val created = repo.createProject("旧名", "旧描述", null)

        val updated = repo.updateProject(created.id, "新名", "新描述", null)

        assertEquals("新名", updated.name)
        assertEquals("新描述", updated.description)
        assertTrue(updated.updatedAt >= created.updatedAt)
        assertEquals(updated, repo.getProject(created.id))
    }

    @Test
    fun deleteProject_removesDirectory() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val created = repo.createProject("待删", "", null)

        repo.deleteProject(created.id)

        assertTrue(!File(root, created.id).exists())
        repo.refresh()
        assertTrue(repo.projects.value.isEmpty())
    }

    @Test
    fun deleteProject_whenDirectoryCannotBeRemoved_throws() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val created = repo.createProject("删不掉", "", null)
        val dir = File(root, created.id)
        // Windows 上占用中的文件删不掉,Linux 上不可写的父目录删不掉子项,两个一起用可以覆盖两种平台。
        val locked = File(dir, "locked.bin").apply { writeText("x") }
        val handle = FileInputStream(locked)
        dir.setWritable(false)

        try {
            val error = runCatching { repo.deleteProject(created.id) }.exceptionOrNull()

            assertTrue("应当抛出 IOException,实际为 $error", error is IOException)
            assertTrue(dir.exists())
        } finally {
            handle.close()
            dir.setWritable(true)
        }
    }

    @Test
    fun deleteProject_whenAlreadyGone_doesNotThrow() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)

        repo.deleteProject("从来不存在的项目")

        assertTrue(repo.projects.value.isEmpty())
    }

    @Test
    fun createProject_withIcon_copiesIconAndExposesFileUri() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val source = File(root, "source.png").apply { writeText("fake") }

        val created = repo.createProject("带图标", "", Uri.fromFile(source).toString())

        assertTrue(created.iconUri!!.startsWith("file:"))
        val iconFile = File(Uri.parse(created.iconUri).path!!)
        assertTrue(
            iconFile.name.matches(
                Regex("icon-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.png")
            )
        )
        assertTrue(iconFile.isFile)
        assertTrue(
            File(File(root, created.id), "project.json")
                .readText()
                .contains("\"iconFileName\":\"${iconFile.name}\"")
        )
    }

    @Test
    fun updateProject_withoutNewIcon_keepsIconUri() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val source = File(root, "source.png").apply { writeText("first") }
        val created = repo.createProject("带图标", "", Uri.fromFile(source).toString())

        val updated = repo.updateProject(created.id, "新名", "新描述", null)

        assertEquals(created.iconUri, updated.iconUri)
    }

    @Test
    fun updateProject_withNewIcon_changesUriAndKeepsPreviousIcon() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val firstSource = File(root, "first.png").apply { writeText("first") }
        val secondSource = File(root, "second.png").apply { writeText("second") }
        val created = repo.createProject("带图标", "", Uri.fromFile(firstSource).toString())
        val previousIcon = File(Uri.parse(created.iconUri).path!!)

        val updated = repo.updateProject(
            created.id,
            "带图标",
            "",
            Uri.fromFile(secondSource).toString()
        )

        assertTrue(created.iconUri != updated.iconUri)
        assertTrue(previousIcon.isFile)
        assertTrue(File(Uri.parse(updated.iconUri).path!!).isFile)
    }

    @Test
    fun createProject_whenIconCannotBeRead_removesIncompleteDirectory() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val missingIcon = Uri.fromFile(File(root, "missing.png")).toString()

        val error = runCatching {
            repo.createProject("失败项目", "", missingIcon)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun updateProject_iconCopyFailure_reportsFailureAndKeepsPreviousProject() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val source = File(root, "source.png").apply { writeText("fake") }
        val created = repo.createProject("带图标", "", Uri.fromFile(source).toString())
        assertTrue(created.iconUri!!.startsWith("file:"))

        val missingIcon = Uri.fromFile(File(root, "missing.png")).toString()
        val error = runCatching {
            repo.updateProject(created.id, "新名", "新描述", missingIcon)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("带图标", repo.getProject(created.id)?.name)
        assertTrue(repo.getProject(created.id)?.iconUri!!.startsWith("file:"))
        assertTrue(File(Uri.parse(created.iconUri).path!!).isFile)
    }

    @Test
    fun getProject_missingOrCorrupt_returnsNull() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        assertNull(repo.getProject("nope"))

        val dir = File(root, "bad").apply { mkdirs() }
        File(dir, "project.json").writeText("{bad")
        assertNull(repo.getProject("bad"))
    }
}
