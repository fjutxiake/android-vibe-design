package com.aeibi.design.data.projects

import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fakeIconCopier = IconCopier { uri, dir ->
        if (uri == null) null else File(dir, "icon.png").apply { writeText("fake") }.name
    }

    private fun repository(root: File) =
        ProjectRepository(root, fakeIconCopier, UnconfinedTestDispatcher())

    @Test
    fun createProject_writesJsonAndListsProject() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)

        val created = repo.createProject("周末去哪", "短途路线", null)

        assertTrue(File(root, created.id).isDirectory)
        assertTrue(File(File(root, created.id), "project.json").exists())
        assertEquals("周末去哪", created.name)
        repo.refresh()
        assertEquals(listOf(created), repo.projects.value)
    }

    @Test
    fun listProjects_skipsCorruptedAndSortsByUpdatedAtDesc() = runTest {
        val root = tmp.newFolder()
        val older = File(root, "a").apply { mkdirs() }
        File(older, "project.json").writeText(
            """{"id":"a","name":"旧","description":"","icon":null,"createdAt":1,"updatedAt":100}""",
        )
        val newer = File(root, "b").apply { mkdirs() }
        File(newer, "project.json").writeText(
            """{"id":"b","name":"新","description":"","icon":null,"createdAt":1,"updatedAt":200}""",
        )
        val corrupt = File(root, "c").apply { mkdirs() }
        File(corrupt, "project.json").writeText("{not-json")

        val repo = repository(root)
        repo.refresh()

        assertEquals(listOf("b", "a"), repo.projects.value.map { it.id })
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
    fun createProject_withIcon_copiesIconAndStoresFilename() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)

        val created = repo.createProject("带图标", "", "content://fake/1")

        assertEquals("icon.png", created.icon)
        assertTrue(File(File(root, created.id), "icon.png").exists())
        assertTrue(repo.iconUri(created)!!.startsWith("file:"))
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
