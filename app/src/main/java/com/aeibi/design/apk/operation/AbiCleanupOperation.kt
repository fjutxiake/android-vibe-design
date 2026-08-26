package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Files

/**
 * 删除不需要的 ABI 原生库目录（如保留 arm64-v8a 时删除其余）。
 *
 * 作用于解码产物的 root/lib/ 目录。
 */
class AbiCleanupOperation : ApkOperation {

    override val name: String = "清理多余 ABI"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val whitelist = context.request.abiWhitelist ?: return
        require(whitelist.isNotEmpty()) { "abiWhitelist 不能为空" }

        val libDir = context.layout.rootDir(context.decodedDir).resolve(LIB_DIR)
        if (!Files.isDirectory(libDir)) return

        var removed = 0
        ApkIo.list(libDir).forEach { abiDir ->
            if (Files.isDirectory(abiDir) && abiDir.fileName.toString() !in whitelist) {
                abiDir.toFile().deleteRecursively()
                removed++
            }
        }
        context.log("ABI: 保留 ${whitelist.joinToString()}，删除 $removed 个目录")
    }

    private companion object {
        const val ORDER = 60
        const val LIB_DIR = "lib"
    }
}
