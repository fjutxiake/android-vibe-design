package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import com.aeibi.design.apk.tool.ToolArgs
import com.aeibi.design.apk.tool.ToolContext
import java.util.Base64

/**
 * 注入前端产物——工具编排示范：把静态项目目录（HTML/CSS/JS/图片）写入
 * assets/frontend_app/（壳运行时 WebView 的加载根目录）。
 *
 * 输入获取（遍历外部目录、读字节）属于编排逻辑；
 * 明文目录内的一切写入全部通过 write_file 工具完成（二进制走 Base64）。
 */
class AssetInjectionOperation : ApkOperation {

    override val name: String = "注入前端产物"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val frontendDir = context.request.frontendDir ?: return
        require(frontendDir.toFile().isDirectory) { "前端产物目录不存在: $frontendDir" }

        val toolContext = ToolContext(decodedDir = context.decodedDir, log = context.log)
        val frontendPrefix = context.decodedDir
            .relativize(context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR).resolve(FRONTEND_DIR))
            .toString()
            .replace('\\', '/') + "/"

        var fileCount = 0
        frontendDir.toFile().walkTopDown().forEach { source ->
            if (!source.isFile) return@forEach
            val relative = frontendDir.relativize(source.toPath()).toString().replace('\\', '/')
            val targetPath = "$frontendPrefix$relative"
            val bytes = source.readBytes()
            val result =
                context.tools.execute(
                    "write_file",
                    toolContext,
                    ToolArgs(
                        path = targetPath,
                        contentBase64 = Base64.getEncoder().encodeToString(bytes)
                    )
                ) ?: error("工具 write_file 未注册")
            if (!result.success) error("注入失败（$relative）: ${result.message}")
            fileCount++
        }
        context.log("前端产物: 注入 $fileCount 个文件 → assets/frontend_app/")
    }

    private companion object {
        const val ORDER = 50
        const val ASSETS_DIR = "assets"
        const val FRONTEND_DIR = "frontend_app"
    }
}
