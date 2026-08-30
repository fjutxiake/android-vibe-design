package com.aeibi.design.apk.engine

import java.nio.file.Path

/**
 * APK 引擎——解码/重打包的最小边界（也是测试注入点）。
 *
 * 当前实现：[AndroidApkEngine]（ARSCLib 纯 Java，手机端）。
 */
interface ApkEngine {

    /** 解码：把模板 APK 解码为明文目录（manifest/资源为可读 XML）。 */
    fun decode(sourceApk: Path, destDir: Path)

    /** 重打包：把明文目录构建回 APK，返回产物自检信息。 */
    fun build(decodedDir: Path, outApk: Path): BuildSummary
}

/**
 * 构建产物摘要（自检信息）——上层可据此做机械验证：
 * 产物是否包含 dex、前端注入是否生效、体积是否合理。
 */
data class BuildSummary(
    /** 产物大小（字节）。 */
    val apkSizeBytes: Long,
    /** ZIP 条目总数。 */
    val entryCount: Int,
    /** 是否包含 dex。 */
    val hasDex: Boolean,
    /** dex 文件数。 */
    val dexCount: Int,
    /** 是否包含前端注入目录（assets/frontend_app/）。 */
    val hasFrontendAssets: Boolean,
    /** 前端文件数（assets/frontend_app/ 下）。 */
    val frontendFileCount: Int
)
