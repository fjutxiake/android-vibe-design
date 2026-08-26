package com.aeibi.design.apk.engine

import java.nio.file.Path

/**
 * 解码产物布局抽象——引擎与操作层之间的契约。
 *
 * 不同引擎的解码产物目录结构可能不同（文件路径、命名、XML 格式），
 * 操作层通过 [ApkLayout] 访问解码产物，不硬编码任何引擎特定路径。
 * 换引擎 = 提供新的 [ApkLayout] 实现，操作层无需改动。
 */
interface ApkLayout {

    /** manifest 文件在解码目录中的位置。 */
    fun manifestFile(decodedDir: Path): Path

    /** res 资源根目录（mipmap/drawable/values 等的父目录）。 */
    fun resRoot(decodedDir: Path): Path

    /** root 文件目录（assets/lib/META-INF 等非资源文件所在）。 */
    fun rootDir(decodedDir: Path): Path
}
