package com.aeibi.design.data.versions.git

import java.io.File

/** 一条版本快照在 git 历史中的呈现。 */
data class GitCommit(val oid: String, val time: Long, val summary: String)

/**
 * 以「外置 git-dir」方式托管某个工作区的 git 仓库：git 元数据独立于工作区目录，
 * workspace/ 保持纯静态文件（预览服务器不会暴露 .git，清空工作区也不影响历史）。
 * 每个实例持有原生句柄，用完必须 close（或 use {}）。
 *
 * 恢复编排在 [com.aeibi.design.data.versions.GitVersionStorage]：先检出目标版本到
 * 独立目录，再整体替换工作区，最后用 [commitAll] 把恢复结果记为新提交。
 */
class Libgit2Repository private constructor(private val handle: Long) : AutoCloseable {

    fun commitAll(message: String): String = Libgit2.commitAll(handle, message)

    fun log(limit: Int = DEFAULT_LOG_LIMIT): List<GitCommit> = Libgit2.log(handle, limit).map { line ->
        val parts = line.split('\t', limit = 3)
        GitCommit(oid = parts[0], time = parts[1].toLong(), summary = parts.getOrElse(2) { "" })
    }

    /** 把 [oidHex] 版本完整检出到 [targetDirectory]；该目录必须是空目录或不存在。 */
    fun checkoutTreeTo(oidHex: String, targetDirectory: File) {
        check(targetDirectory.isDirectory || targetDirectory.mkdirs()) {
            "恢复目录不可用: $targetDirectory"
        }
        Libgit2.checkoutTree(handle, oidHex, targetDirectory.absolutePath)
    }

    /** 工作区/索引相对 HEAD 是否有未提交改动（含未跟踪文件；ignored 文件不计）。 */
    fun hasUncommittedChanges(): Boolean = Libgit2.isDirty(handle)

    override fun close() {
        Libgit2.close(handle)
    }

    companion object {

        private const val DEFAULT_LOG_LIMIT = 50

        fun libgit2Version(): String = Libgit2.version()

        /** 打开 [workspace] 对应的仓库；git 元数据存放在 [gitDir]，不存在时自动初始化。 */
        fun openOrInit(workspace: File, gitDir: File): Libgit2Repository {
            check(workspace.isDirectory || workspace.mkdirs()) { "工作区不存在: $workspace" }
            return Libgit2Repository(Libgit2.openOrInit(workspace.absolutePath, gitDir.absolutePath))
        }
    }
}
