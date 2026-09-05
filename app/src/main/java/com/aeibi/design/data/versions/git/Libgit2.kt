package com.aeibi.design.data.versions.git

/**
 * libgit2 JNI 原生接口的薄声明；原生句柄由 [Libgit2Repository] 持有并负责释放。
 * 原型未做跨线程同步（git_libgit2_init 只在首次调用时执行一次），调用方需限定在 IO 线程。
 */
internal object Libgit2 {

    init {
        System.loadLibrary("vibe_git")
    }

    external fun version(): String

    /** 打开（不存在时初始化）一个 git-dir 与工作区分离的仓库，返回原生句柄。 */
    external fun openOrInit(workDir: String, gitDir: String): Long

    /** 等价 `git add -A` 后提交，返回新提交的 SHA-1。 */
    external fun commitAll(handle: Long, message: String): String

    /** 按时间倒序返回最近 limit 条提交，每条格式 `oid\ttime\tsummary`。 */
    external fun log(handle: Long, limit: Int): Array<String>

    /**
     * 把 [oidHex] 版本完整检出到 [targetDirectory]（独立目录，不触碰当前工作区与
     * HEAD）。恢复的原子性由调用方在该目录上用「pending + 原子移动」模式保证。
     */
    external fun checkoutTree(handle: Long, oidHex: String, targetDirectory: String?)

    /** 工作区/索引相对 HEAD 是否有未提交改动（含未跟踪文件；ignored 文件不计）。 */
    external fun isDirty(handle: Long): Boolean

    external fun close(handle: Long)
}
