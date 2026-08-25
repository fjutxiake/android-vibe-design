package com.aeibi.design.data.projects

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 先把内容写进同目录下的临时文件,写完后再原子替换目标文件。
 *
 * 直接覆盖目标文件时,进程崩溃、磁盘写满等中途失败会留下被截断的半个文件;
 * 改成"临时文件 + 原子重命名"后,目标文件要么是旧内容,要么是完整的新内容。
 * 临时文件放在同一目录,保证重命名不跨文件系统。
 */
internal fun File.writeAtomically(write: (File) -> Unit) {
    val temp = File(parentFile, "$name.tmp")
    try {
        write(temp)
        Files.move(temp.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        temp.delete()
    }
}
