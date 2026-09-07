package com.aeibi.design.data.versions

import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.projects.recoverInterruptedReplacement
import com.aeibi.design.data.projects.replaceWorkspaceDirectory
import com.aeibi.design.data.versions.git.Libgit2Repository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 libgit2 的版本存储：git 元数据外置在 `projects/<id>/versions.git/`，
 * 工作区保持纯静态文件（预览服务器不会暴露 .git，清空工作区不影响历史）。
 *
 * 提交信息编码为 `TRIGGER: label`（如 `MANUAL: 手动快照`），人可直接读，
 * 也能被 [listVersions] 解析回结构化字段；无法解析的旧消息按 MANUAL 兜底。
 */
class GitVersionStorage(
    private val projectRepository: ProjectRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VersionStorage {

    override suspend fun snapshot(projectId: String, label: String, trigger: VersionTrigger): VersionSnapshot =
        withContext(ioDispatcher) {
            openRepository(projectId).use { repo ->
                val oid = repo.commitAll(encodeMessage(trigger, label))
                val head = repo.log(1).first()
                VersionSnapshot(
                    id = oid,
                    projectId = projectId,
                    label = label,
                    createdAt = head.time * MILLIS_PER_SECOND,
                    trigger = trigger
                )
            }
        }

    override suspend fun listVersions(projectId: String): List<VersionSnapshot> = withContext(ioDispatcher) {
        openRepository(projectId).use { repo ->
            repo.log(MAX_LIST).map { commit ->
                val (trigger, label) = parseMessage(commit.summary)
                VersionSnapshot(
                    id = commit.oid,
                    projectId = projectId,
                    label = label,
                    createdAt = commit.time * MILLIS_PER_SECOND,
                    trigger = trigger
                )
            }
        }
    }

    override suspend fun hasUncommittedChanges(projectId: String): Boolean = withContext(ioDispatcher) {
        openRepository(projectId).use { repo -> repo.hasUncommittedChanges() }
    }

    override suspend fun restore(projectId: String, snapshotId: String, label: String) {
        withContext(ioDispatcher) {
            openRepository(projectId).use { repo ->
                val workspace = projectRepository.workspaceDirectory(projectId)
                val projectDir = checkNotNull(workspace.parentFile) { "工作区目录异常: $workspace" }

                // 1) 目标版本完整检出到 pending 目录：恢复过程绝不逐文件直写工作区，
                //    进程中途被杀最多留下 pending 孤儿（下次打开时清理），工作区保持原状。
                val pending = File(projectDir, PENDING_DIR)
                pending.deleteRecursively()
                check(pending.isDirectory || pending.mkdirs()) { "恢复暂存目录不可用: $pending" }
                repo.checkoutTreeTo(snapshotId, pending)

                // 2) 复用项目既有的 pending + 原子移动模式整体替换工作区；
                //    旧工作区连同未跟踪/ignored 文件一并消失，不会污染恢复结果。
                replaceWorkspaceDirectory(projectDir, pending)

                // 3) add_all 具备 add -A 语义，索引与工作区重建对齐后记为新提交。
                repo.commitAll(encodeMessage(VersionTrigger.RESTORE, label))
            }
        }
    }

    private fun openRepository(projectId: String): Libgit2Repository {
        val workspace = projectRepository.workspaceDirectory(projectId)
        val projectDir = checkNotNull(workspace.parentFile) { "工作区目录异常: $workspace" }
        val gitDir = File(projectDir, GIT_DIR)
        // 进程中途被杀的残留物：被打断的整体替换（backup 回滚/清理）、git 索引锁
        // 与恢复暂存目录。单进程应用里不存在并发写者，打开仓库时直接清除即可
        // 回到一致状态。backup 回滚必须在 openOrInit 之前——后者会对缺失的
        // 工作区静默建空目录，掩盖可恢复状态。
        recoverInterruptedReplacement(projectDir)
        File(gitDir, "index.lock").delete()
        File(projectDir, PENDING_DIR).deleteRecursively()
        return Libgit2Repository.openOrInit(workspace, gitDir)
    }

    private fun encodeMessage(trigger: VersionTrigger, label: String): String = "${trigger.name}: $label"

    private fun parseMessage(summary: String): Pair<VersionTrigger, String> {
        val match = MESSAGE_PATTERN.find(summary) ?: return VersionTrigger.MANUAL to summary
        val trigger = runCatching { VersionTrigger.valueOf(match.groupValues[1]) }
            .getOrNull()
            ?: return VersionTrigger.MANUAL to summary
        return trigger to match.groupValues[2]
    }

    private companion object {
        const val GIT_DIR = "versions.git"

        // 复用项目层的 pending 目录名：markInitialized / initializeFromTemplate 等既有
        // 清理路径也会顺带清扫恢复孤儿。
        val PENDING_DIR = ProjectRepository.PENDING_WORKSPACE_DIR
        const val MAX_LIST = 200
        const val MILLIS_PER_SECOND = 1000L
        val MESSAGE_PATTERN = Regex("^([A-Z_]+): (.*)$")
    }
}
