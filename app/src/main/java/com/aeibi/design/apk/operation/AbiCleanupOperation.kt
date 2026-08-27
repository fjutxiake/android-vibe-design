package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import com.aeibi.design.apk.tool.ToolArgs
import com.aeibi.design.apk.tool.ToolContext

/**
 * 删除不需要的 ABI 原生库目录——工具编排示范：list_files + delete_file。
 */
class AbiCleanupOperation : ApkOperation {

    override val name: String = "清理多余 ABI"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val whitelist = context.request.abiWhitelist ?: return
        require(whitelist.isNotEmpty()) { "abiWhitelist 不能为空" }

        val libDir = context.decodedDir
            .relativize(context.layout.rootDir(context.decodedDir).resolve(LIB_DIR))
            .toString()
        val toolContext = ToolContext(decodedDir = context.decodedDir, log = context.log)

        // list_files：列出 lib/ 下 ABI 目录
        val list = context.tools.execute("list_files", toolContext, ToolArgs(path = libDir))
            ?: error("工具 list_files 未注册")
        if (!list.success) return
        val entries = list.data?.get("entries") as? List<*> ?: return

        var removed = 0
        entries.map { it.toString() }.filter { it !in whitelist }.forEach { abi ->
            // delete_file：删除非白名单 ABI 目录
            val result = context.tools.execute("delete_file", toolContext, ToolArgs(path = "$libDir/$abi"))
                ?: error("工具 delete_file 未注册")
            if (result.success) removed++
        }
        context.log("ABI: 保留 ${whitelist.joinToString()}，删除 $removed 个目录")
    }

    private companion object {
        const val ORDER = 60
        const val LIB_DIR = "lib"
    }
}
