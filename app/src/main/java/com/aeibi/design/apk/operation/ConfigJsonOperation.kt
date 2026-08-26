package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Files

/**
 * 写入 assets/app_config.json（壳的运行时配置）。
 *
 * 解码产物中 root 文件位于 <decoded>/root/，assets 缺失时按需创建。
 */
class ConfigJsonOperation : ApkOperation {

    override val name: String = "写入应用配置"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val config = context.request.config ?: return
        require(config.isNotEmpty()) { "config 不能为空" }

        val assetsDir = context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR)
        Files.createDirectories(assetsDir)

        val json = config.entries
            .joinToString(",\n  ") { (key, value) -> """  "$key": "${escape(value)}"""" }
        ApkIo.writeString(assetsDir.resolve(CONFIG_FILE), "{\n$json\n}")
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
