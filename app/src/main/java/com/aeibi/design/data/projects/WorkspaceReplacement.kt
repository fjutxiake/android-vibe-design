package com.aeibi.design.data.projects

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 整体替换期间暂时接管旧工作区的目录名；中断后的磁盘状态以此判断。 */
internal const val BACKUP_WORKSPACE_DIR = "workspace.backup"

/**
 * 用 pending 目录整体替换工作区：项目创建、模板初始化与版本恢复共用同一安全模式。
 *
 * 崩溃安全序列：旧工作区先改名成 backup（同目录 rename，原子完成），再把 pending
 * 原子移动为工作区，最后删除 backup。两步之间被杀，磁盘上要么是旧工作区（工作区
 * 缺失、backup 在场），要么是新工作区（backup 成垃圾），不存在旧方案"先删后移"
 * 窗口期的空工作区。任何一种中间状态都由 [recoverInterruptedReplacement] 在下次
 * 使用工作区前回收。
 */
internal fun replaceWorkspaceDirectory(projectDir: File, pendingWorkspace: File) {
    recoverInterruptedReplacement(projectDir)

    val workspace = File(projectDir, WORKSPACE_DIR)
    val backup = File(projectDir, BACKUP_WORKSPACE_DIR)
    if (workspace.exists()) {
        try {
            atomicMove(workspace, backup)
        } catch (error: Exception) {
            throw IOException("Could not back up workspace: ${workspace.path}", error)
        }
    }
    try {
        atomicMove(pendingWorkspace, workspace)
    } catch (error: Exception) {
        // 回滚失败时保持"工作区缺失 + backup 在场"，留给下次打开时恢复；
        // 不能像旧实现那样 mkdir 空工作区，把可恢复状态掩盖成数据丢失。
        try {
            atomicMove(backup, workspace)
        } catch (rollbackError: Exception) {
            error.addSuppressed(rollbackError)
        }
        throw error
    }
    // 删除失败无妨：工作区已是新内容，残留 backup 由下次恢复逻辑清扫。
    backup.deleteRecursively()
}

/**
 * 回收一次被打断的整体替换：
 * - 工作区缺失且 backup 在场：替换在移入 pending 前被杀，把 backup 移回工作区，
 *   回到替换前的状态，调用方可以安全重试。
 * - 工作区在场且 backup 在场：替换已完成、backup 未及删除，backup 是纯垃圾。
 *
 * 恢复动作失败时抛出，让调用方停在原地。这个顺序很关键：openOrInit 之类会对
 * 缺失的工作区静默建空目录，若先放行它们，backup 会被当作垃圾清掉，旧工作区
 * 内容（含未提交改动，git 历史里没有）就真的丢了。
 */
internal fun recoverInterruptedReplacement(projectDir: File) {
    val backup = File(projectDir, BACKUP_WORKSPACE_DIR)
    if (!backup.exists()) return

    val workspace = File(projectDir, WORKSPACE_DIR)
    if (workspace.exists()) {
        if (!backup.deleteRecursively() && backup.exists()) {
            throw IOException("Could not clean workspace backup: ${backup.path}")
        }
        return
    }
    try {
        atomicMove(backup, workspace)
    } catch (error: Exception) {
        throw IOException("Could not restore workspace backup: ${backup.path}", error)
    }
}

private fun atomicMove(source: File, target: File) {
    try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath())
    }
}
