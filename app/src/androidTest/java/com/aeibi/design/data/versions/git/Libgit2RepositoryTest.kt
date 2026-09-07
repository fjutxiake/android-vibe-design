package com.aeibi.design.data.versions.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Libgit2RepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun openRepository(projectDir: File): Libgit2Repository =
        Libgit2Repository.openOrInit(File(projectDir, "workspace"), File(projectDir, "versions.git"))

    @Test
    fun openOrInit_loadsNativeLibraryAndReportsVersion() {
        openRepository(tmp.newFolder()).use { _ ->
            assertTrue(Libgit2Repository.libgit2Version().isNotEmpty())
        }
    }

    @Test
    fun commitAll_createsHistoryNewestFirst() {
        val projectDir = tmp.newFolder()
        val workspace = File(projectDir, "workspace")
        openRepository(projectDir).use { repo ->
            File(workspace, "index.html").writeText("<h1>v1</h1>")
            val first = repo.commitAll("snapshot 1")

            File(workspace, "index.html").writeText("<h1>v2</h1>")
            File(workspace, "extra.js").writeText("console.log('new')")
            val second = repo.commitAll("snapshot 2")

            assertTrue(first != second)
            val history = repo.log(10)
            assertEquals(listOf(second, first), history.map { it.oid })
            assertEquals("snapshot 2", history.first().summary)
        }
    }

    @Test
    fun hasUncommittedChanges_tracksModifiedAndUntrackedFiles() {
        val projectDir = tmp.newFolder()
        val workspace = File(projectDir, "workspace")
        openRepository(projectDir).use { repo ->
            File(workspace, "index.html").writeText("one")
            repo.commitAll("first")
            assertFalse(repo.hasUncommittedChanges())

            File(workspace, "index.html").writeText("two")
            assertTrue(repo.hasUncommittedChanges())

            repo.commitAll("second")
            File(workspace, "untracked.tmp").writeText("x")
            assertTrue(repo.hasUncommittedChanges())

            repo.commitAll("third")
            assertFalse(repo.hasUncommittedChanges())
        }
    }

    @Test
    fun checkoutTreeTo_writesTargetTreeWithoutTouchingWorkspace() {
        val projectDir = tmp.newFolder()
        val workspace = File(projectDir, "workspace")
        openRepository(projectDir).use { repo ->
            File(workspace, "index.html").writeText("v1")
            val first = repo.commitAll("snapshot 1")

            File(workspace, "index.html").writeText("v2")
            File(workspace, "extra.js").writeText("x")
            repo.commitAll("snapshot 2")

            val target = File(projectDir, "restore-pending").apply { mkdirs() }
            repo.checkoutTreeTo(first, target)

            // 目标目录就是 first 的完整内容,当前工作区不受影响。
            assertEquals("v1", File(target, "index.html").readText())
            assertFalse(File(target, "extra.js").exists())
            assertEquals("v2", File(workspace, "index.html").readText())
            assertTrue(File(workspace, "extra.js").exists())
        }
    }

    @Test
    fun reopeningRepository_keepsHistoryAndGitDirStaysOutsideWorkspace() {
        val projectDir = tmp.newFolder()
        val workspace = File(projectDir, "workspace")
        openRepository(projectDir).use { repo ->
            File(workspace, "index.html").writeText("one")
            repo.commitAll("first")
        }

        openRepository(projectDir).use { repo ->
            assertEquals(1, repo.log(10).size)
            File(workspace, "index.html").writeText("two")
            repo.commitAll("second")
            assertEquals(2, repo.log(10).size)
        }

        assertFalse(File(workspace, ".git").exists())
        assertTrue(File(projectDir, "versions.git").isDirectory)
    }

    @Test
    fun log_onFreshRepository_returnsEmptyList() {
        openRepository(tmp.newFolder()).use { repo ->
            assertTrue(repo.log(10).isEmpty())
        }
    }
}
