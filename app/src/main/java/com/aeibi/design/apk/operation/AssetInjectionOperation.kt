package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 注入前端产物：把静态项目目录（HTML/CSS/JS/图片）复制到
 * assets/frontend_app/——壳运行时 LocalHttpServer 的托管根目录。
 */
class AssetInjectionOperation : ApkOperation {

    override val name: String = "注入前端产物"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val frontendDir = context.request.frontendDir ?: return
        require(Files.isDirectory(frontendDir)) { "前端产物目录不存在: $frontendDir" }

        val target = context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR).resolve(FRONTEND_DIR)
        Files.createDirectories(target)

        ApkIo.walk(frontendDir).forEach { source ->
            val relative = frontendDir.relativize(source)
            val destination = target.resolve(relative.toString())
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        context.log("前端产物: 注入 ${frontendDir.fileName} → assets/frontend_app/")
    }

    private companion object {
        const val ORDER = 50
        const val ASSETS_DIR = "assets"
        const val FRONTEND_DIR = "frontend_app"
    }
}
