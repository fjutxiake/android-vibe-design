package com.aeibi.design.apk.tool

import java.nio.file.Files
import java.nio.file.Path

/**
 * 工具层共享支持：路径安全解析（防止穿越明文目录）。
 */
internal object FileToolSupport {

    /**
     * 把相对路径解析为明文目录内的绝对路径。
     * 解析后必须仍在 [decodedDir] 内，否则拒绝（路径穿越防护）。
     */
    fun resolve(decodedDir: Path, relative: String?): Result<Path> {
        if (relative.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("缺少 path 参数"))
        }
        val target = decodedDir.resolve(relative).normalize()
        if (!target.startsWith(decodedDir.normalize())) {
            return Result.failure(IllegalArgumentException("路径越界（仅允许操作明文目录内文件）: $relative"))
        }
        return Result.success(target)
    }

    fun requireFile(target: Path): Result<Path> {
        if (!Files.exists(target)) return Result.failure(IllegalArgumentException("文件不存在: $target"))
        if (Files.isDirectory(target)) return Result.failure(IllegalArgumentException("是目录而非文件: $target"))
        return Result.success(target)
    }
}
