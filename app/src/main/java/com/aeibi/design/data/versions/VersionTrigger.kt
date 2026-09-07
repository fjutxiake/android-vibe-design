package com.aeibi.design.data.versions

/** 快照的触发来源，决定版本列表中的徽标展示与后续构建循环的挂钩行为。 */
enum class VersionTrigger {
    /** 项目初始化完成后的第一个快照。 */
    INIT,

    /** Agent 每轮构建动作前的自动快照（构建循环合入后启用）。 */
    AUTO_BUILD,

    /** 用户在版本页手动创建的快照。 */
    MANUAL,

    /** 恢复操作生成的记录：内容为被恢复的旧版本，历史因此保持线性。 */
    RESTORE,

    /**
     * 恢复前对未提交工作区改动做的保护性快照。没有它，恢复会直接覆盖未提交
     * 内容；有了它，未提交改动随时可以通过再次恢复找回。
     */
    PRE_RESTORE
}
