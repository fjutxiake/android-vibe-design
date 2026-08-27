package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import com.aeibi.design.apk.tool.ToolArgs
import com.aeibi.design.apk.tool.ToolContext

/**
 * 写入 assets/app_config.json（壳的运行时配置）——工具编排示范：write_file。
 */
class ConfigJsonOperation : ApkOperation {

    override val name: String = "写入应用配置"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val config = context.request.config ?: return
        require(config.isNotEmpty()) { "config 不能为空" }

        val json = config.entries
            .joinToString(",\n  ") { (key, value) -> """  "$key": "${escape(value)}"""" }
        val path = context.decodedDir
            .relativize(context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR).resolve(CONFIG_FILE))
            .toString()

        val result = context.tools.execute(
            "write_file",
            ToolContext(decodedDir = context.decodedDir, log = context.log),
            ToolArgs(path = path, content = "{\n$json\n}")
        ) ?: error("工具 write_file 未注册")
        if (!result.success) error("写入失败: ${result.message}")

        context.log("配置: 写入 ${CONFIG_FILE}（${config.size} 项）")
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private companion object {
        const val ORDER = 40
        const val ASSETS_DIR = "assets"
        const val CONFIG_FILE = "app_config.json"
    }
}
