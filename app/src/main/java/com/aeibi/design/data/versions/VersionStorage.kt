package com.aeibi.design.data.versions

/**
 * 版本快照的存储后端。当前唯一实现是 [GitVersionStorage]（libgit2，外置 git-dir）；
 * 接口保持极薄，将来若需要内容寻址存储或 JGit 替换，只换实现不动调用方。
 *
 * 设计说明（对应 issue #36 的决策）：
 * - 不引入 Room `versions` 元数据表：git 的提交历史本身携带
 *   id/label/created_at，另建一张表会形成双源真相，同步成本大于收益。
 * - 不做保留策略（每项目最近 N 条）：git 对象天然去重，模板规模的产物
 *   体积可以忽略；截断 git 历史需要改写，不值得在 MVP 引入。
 */
interface VersionStorage {

    /** 创建一条快照并返回其元数据。 */
    suspend fun snapshot(projectId: String, label: String, trigger: VersionTrigger): VersionSnapshot

    /** 按创建时间倒序列出指定项目的全部快照。 */
    suspend fun listVersions(projectId: String): List<VersionSnapshot>

    /** 工作区/索引相对最新快照是否有未提交改动（含未跟踪文件；ignored 文件不计）。 */
    suspend fun hasUncommittedChanges(projectId: String): Boolean

    /**
     * 把工作区恢复到 [snapshotId] 对应的快照。恢复本身会生成一条新记录
     * （trigger 为 RESTORE），历史保持线性，因此恢复可以再次被撤销。
     * 未提交改动的 PRE_RESTORE 保护由 [VersionSnapshotService] 负责编排。
     */
    suspend fun restore(projectId: String, snapshotId: String, label: String)
}
