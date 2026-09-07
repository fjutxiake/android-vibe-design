package com.aeibi.design.data.versions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.versions.git.Libgit2Repository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VersionSnapshotServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repository(root: File) = ProjectRepository(
        root,
        context.contentResolver,
        context.assets,
        UnconfinedTestDispatcher()
    )

    private fun service(root: File): VersionSnapshotService {
        val projectRepository = repository(root)
        return VersionSnapshotService(
            GitVersionStorage(projectRepository, UnconfinedTestDispatcher()),
            projectRepository,
            UnconfinedTestDispatcher()
        )
    }

    private suspend fun newInitializedProject(root: File): String {
        val repo = repository(root)
        val project = repo.createProject("测试项目", "", null)
        repo.markInitialized(project.id)
        return project.id
    }

    private fun pendingDir(root: File, projectId: String) = File(File(root, projectId), "workspace.pending")

    private fun backupDir(root: File, projectId: String) = File(File(root, projectId), "workspace.backup")

    @Test
    fun ensureInitialSnapshot_createsInitVersionOnce() = runTest {
        val root = tmp.newFolder()
        val service = service(root)
        val id = newInitializedProject(root)

        service.ensureInitialSnapshot(id)
        service.ensureInitialSnapshot(id)

        val versions = service.snapshots(id)
        assertEquals(1, versions.size)
        assertEquals(VersionTrigger.INIT, versions.single().trigger)
    }

    @Test
    fun ensureInitialSnapshot_skipsUninitializedProject() = runTest {
        val root = tmp.newFolder()
        val project = repository(root).createProject("未初始化", "", null)
        val service = service(root)

        service.ensureInitialSnapshot(project.id)

        assertTrue(service.snapshots(project.id).isEmpty())
    }

    @Test
    fun createSnapshot_onUninitializedProject_throws() = runTest {
        val root = tmp.newFolder()
        val project = repository(root).createProject("未初始化", "", null)
        val service = service(root)

        val error = runCatching { service.createSnapshot(project.id, "快照") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun snapshotAndRestore_fullChain_keepsLinearHistory() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)

        service.ensureInitialSnapshot(id)
        File(workspace, "index.html").writeText("v1")
        val first = service.createSnapshot(id, "第一次修改")
        File(workspace, "index.html").writeText("v2")
        File(workspace, "extra.js").writeText("x")
        val second = service.createSnapshot(id, "第二次修改")

        // 恢复时无未提交改动,不应产生 PRE_RESTORE 记录。
        service.restore(id, first.id, "恢复到 ${first.id.take(7)}")

        // 工作区回滚到 first,未跟踪文件随整目录替换一并清除。
        assertEquals("v1", File(workspace, "index.html").readText())
        assertFalse(File(workspace, "extra.js").exists())
        assertFalse(pendingDir(root, id).exists())

        // 历史线性:恢复是最新一条,旧记录全部保留,可再次恢复。
        val versions = service.snapshots(id)
        assertEquals(4, versions.size)
        assertEquals(VersionTrigger.RESTORE, versions.first().trigger)
        assertTrue(versions.first().label.startsWith("恢复到 "))
        assertEquals(
            listOf(versions.first().id, second.id, first.id, versions.last().id),
            versions.map { it.id }
        )
        assertEquals(VersionTrigger.INIT, versions.last().trigger)
        assertTrue(versions.zipWithNext().all { (newer, older) -> newer.createdAt >= older.createdAt })
    }

    @Test
    fun restore_withUncommittedChanges_preservesThemAsPreRestoreSnapshot() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)

        service.ensureInitialSnapshot(id)
        File(workspace, "index.html").writeText("v1")
        val first = service.createSnapshot(id, "第一次修改")
        File(workspace, "index.html").writeText("v2")
        val second = service.createSnapshot(id, "第二次修改")

        // 未提交改动:已跟踪文件改内容 + 新增未跟踪文件。
        File(workspace, "index.html").writeText("v3 未提交")
        File(workspace, "scratch.tmp").writeText("草稿")

        service.restore(id, first.id, "恢复到 ${first.id.take(7)}")

        // 恢复生效:工作区是 v1,未提交内容不残留。
        assertEquals("v1", File(workspace, "index.html").readText())
        assertFalse(File(workspace, "scratch.tmp").exists())

        // PRE_RESTORE 记录完整保存了未提交改动,恢复到它即可找回。
        val versions = service.snapshots(id)
        assertEquals(5, versions.size)
        assertEquals(VersionTrigger.RESTORE, versions[0].trigger)
        assertEquals(VersionTrigger.PRE_RESTORE, versions[1].trigger)

        service.restore(id, versions[1].id, "找回未提交改动")
        assertEquals("v3 未提交", File(workspace, "index.html").readText())
        assertEquals("草稿", File(workspace, "scratch.tmp").readText())
    }

    @Test
    fun restore_clearsStalePendingDirectory() = runTest {
        val root = tmp.newFolder()
        val service = service(root)
        val id = newInitializedProject(root)
        service.ensureInitialSnapshot(id)
        // 模拟恢复中途被杀留下的孤儿暂存目录。
        pendingDir(root, id).apply {
            mkdirs()
            File(this, "junk").writeText("x")
        }

        service.restore(id, service.snapshots(id).single().id, "恢复")

        assertFalse(pendingDir(root, id).exists())
        assertEquals(2, service.snapshots(id).size)
    }

    @Test
    fun snapshots_recoversWorkspaceAfterInterruptedReplacement() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)
        val projectDir = checkNotNull(workspace.parentFile)
        service.ensureInitialSnapshot(id)
        File(workspace, "index.html").writeText("v1")
        service.createSnapshot(id, "v1")

        // 模拟整体替换在「旧工作区已改名 backup、pending 尚未移入」之间被杀:
        // 工作区缺失,backup 承载着替换前的内容(含未提交改动,git 历史里没有)。
        check(workspace.renameTo(backupDir(root, id)))
        pendingDir(root, id).apply {
            mkdirs()
            File(this, "half-checked-out").writeText("target")
        }

        // 下一次版本操作应先回滚 backup,再继续正常打开仓库。
        assertEquals(2, service.snapshots(id).size)

        assertEquals("v1", File(workspace, "index.html").readText())
        assertFalse(backupDir(root, id).exists())
        assertFalse(pendingDir(root, id).exists())
    }

    @Test
    fun snapshots_cleansBackupAfterCompletedReplacement() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)
        service.ensureInitialSnapshot(id)

        // 模拟整体替换已完成但 backup 未及删除:工作区是新内容,backup 是旧内容。
        check(workspace.renameTo(backupDir(root, id)))
        check(workspace.mkdir())
        File(workspace, "index.html").writeText("v2 已恢复")

        service.snapshots(id)

        assertFalse(backupDir(root, id).exists())
        assertEquals("v2 已恢复", File(workspace, "index.html").readText())
    }

    @Test
    fun snapshotBeforeBuildRound_recordsAutoBuildTrigger() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)
        service.ensureInitialSnapshot(id)

        // 干净工作区:HEAD 本身就是回滚点,构建前不应产生空提交。
        service.snapshotBeforeBuildRound(id)
        assertEquals(1, service.snapshots(id).size)

        File(workspace, "index.html").writeText("未提交的改动")
        service.snapshotBeforeBuildRound(id)

        val versions = service.snapshots(id)
        assertEquals(2, versions.size)
        assertEquals(VersionTrigger.AUTO_BUILD, versions.first().trigger)
        assertEquals("构建前快照", versions.first().label)
    }

    @Test
    fun createSnapshot_clearsStaleIndexLock() = runTest {
        val root = tmp.newFolder()
        val service = service(root)
        val id = newInitializedProject(root)
        service.ensureInitialSnapshot(id)

        // 模拟进程中途被杀残留的锁文件,下次打开仓库时应被清除而不是报错。
        val gitDir = File(File(root, id), "versions.git")
        File(gitDir, "index.lock").writeText("")

        service.createSnapshot(id, "锁残留后的快照")

        assertEquals(2, service.snapshots(id).size)
    }

    @Test
    fun snapshots_parsesLegacyMessagesGracefully() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val id = newInitializedProject(root)
        val workspace = repo.workspaceDirectory(id)
        // 旧版原型直接写裸消息(无 TRIGGER 前缀),应兜底解析为 MANUAL。
        Libgit2Repository.openOrInit(workspace, File(workspace.parentFile, "versions.git")).use { git ->
            File(workspace, "a.txt").writeText("1")
            git.commitAll("手动快照")
        }

        val versions = service(root).snapshots(id)

        assertEquals(1, versions.size)
        assertEquals(VersionTrigger.MANUAL, versions.single().trigger)
        assertEquals("手动快照", versions.single().label)
    }

    @Test
    fun deleteProject_removesVersionStore() = runTest {
        val root = tmp.newFolder()
        val repo = repository(root)
        val service = service(root)
        val id = newInitializedProject(root)
        service.ensureInitialSnapshot(id)
        assertTrue(File(File(root, id), "versions.git").isDirectory)

        repo.deleteProject(id)

        assertFalse(File(root, id).exists())
    }
}
