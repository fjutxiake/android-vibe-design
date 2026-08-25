package com.aeibi.design.data.projects

import java.io.File
import java.io.InputStream

/** 把 [uri] 的内容拷贝进 [projectDir],返回图标文件名(如 "icon.png");无图标或失败返回 null。 */
fun interface IconCopier {
    fun copy(uri: String?, projectDir: File): String?
}

/**
 * 把 [openInput] 提供的字节写成 [projectDir] 下的 icon.[extension],返回图标文件名。
 *
 * 直接写目标文件时,拷贝到一半失败会把上一张图标截断,而仓库里仍然记着这个文件名,
 * 结果就是图标变成一张坏图。这里先写同目录的临时文件,拷完再原子替换,
 * 失败时旧图标原样保留。[openInput] 返回 null 表示来源打不开,此时不动任何文件。
 */
internal fun copyIconAtomically(projectDir: File, extension: String, openInput: () -> InputStream?): String? {
    val input = openInput() ?: return null
    val target = File(projectDir, "icon.$extension")
    input.use { source ->
        target.writeAtomically { temp -> temp.outputStream().use { source.copyTo(it) } }
    }
    return target.name
}
