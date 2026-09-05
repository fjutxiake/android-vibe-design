package com.aeibi.design.data.projects

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 工作区 zip 打包/解压（导入导出流）。
 *
 * - 导出：workspace 内容递归打包（相对路径条目，无根前缀）
 * - 导入：解压到目标目录，防 zip-slip（条目路径规范化后必须留在目标内）
 */
object WorkspaceZip {

    /** 把目录打包为 zip（相对路径条目）。 */
    fun exportDirectory(sourceDir: File, zipFile: File) {
        if (!sourceDir.isDirectory) throw IOException("Not a directory: $sourceDir")
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(zipFile.outputStream().buffered()).use { out ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                out.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    /** 把 zip 文件解压到目标目录（防路径穿越）。 */
    fun importArchive(zipFile: File, targetDir: File): Int {
        if (!zipFile.isFile) throw IOException("Not a file: $zipFile")
        return zipFile.inputStream().use { importArchive(it, targetDir) }
    }

    /** 从输入流解压 zip 到目标目录（SAF 场景：uri 流直接解压，防路径穿越）。 */
    fun importArchive(input: InputStream, targetDir: File): Int {
        targetDir.mkdirs()
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val resolved = safeResolve(targetDir, entry.name)
                if (resolved != null) {
                    if (entry.isDirectory) {
                        resolved.mkdirs()
                    } else {
                        resolved.parentFile?.mkdirs()
                        resolved.outputStream().use { out -> zip.copyTo(out) }
                        count++
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }

    /** zip-slip 防护：条目名规范化后必须留在 targetDir 内；危险条目返回 null（跳过）。 */
    private fun safeResolve(targetDir: File, entryName: String): File? {
        val normalized = File(targetDir, entryName).normalize()
        if (!normalized.toPath().startsWith(targetDir.toPath())) return null
        return normalized
    }
}
