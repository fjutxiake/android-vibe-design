package com.aeibi.design.apk

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * java.nio.file 兼容层：Android 的 Files/Path 只实现了 Java 7 子集，
 * 没有 readString/writeString/walk/list/createTempDirectory 等 Java 8+ 方法。
 *
 * 本工具统一提供 Android 可用的等价实现（JVM 测试环境同样可用）。
 */
object ApkIo {

    /** Android 兼容 readString。 */
    fun readString(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)

    /** Android 兼容 writeString。 */
    fun writeString(path: Path, content: String) {
        Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
    }

    /** Android 兼容 walk（递归遍历，含自身）。 */
    fun walk(path: Path): Sequence<Path> = path.toFile().walkTopDown().map(File::toPath)

    /** Android 兼容 list（仅直接子项）。 */
    fun list(path: Path): List<Path> = path.toFile().listFiles()?.map(File::toPath) ?: emptyList()

    /** Android 兼容 createTempDirectory。 */
    fun createTempDir(prefix: String): Path {
        val file = File.createTempFile(prefix, "")
        file.delete()
        file.mkdirs()
        return file.toPath()
    }
}
