package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 替换应用图标：把用户图标复制到所有密度目录的启动图标条目。
 *
 * 策略：壳模板预置固定资源 id（ic_launcher），本操作只替换
 * 各 mipmap 密度目录下 ic_launcher 文件的内容，不动 arsc 的资源表结构——
 * 避免最复杂的"arsc 增项"操作。
 */
class IconOperation : ApkOperation {

    override val name: String = "替换应用图标"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val iconFile = context.request.iconFile ?: return
        require(Files.exists(iconFile)) { "图标文件不存在: $iconFile" }

        var replaced = 0
        ApkIo.walk(context.layout.resRoot(context.decodedDir))
            .filter { Files.isRegularFile(it) }
            .filter { isLauncherIcon(it) }
            .forEach { target ->
                Files.copy(iconFile, target, StandardCopyOption.REPLACE_EXISTING)
                replaced++
            }
        context.log("图标: 替换 $replaced 个启动图标条目")
    }

    private fun isLauncherIcon(file: Path): Boolean {
        val name = file.fileName.toString()
        val dir = file.parent?.fileName?.toString() ?: return false
        val isIconName = name.substringBeforeLast('.') in setOf(ICON_LAUNCHER, ICON_LAUNCHER_ROUND)
        val isBitmap = name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".jpg")
        return dir.startsWith(MIPMAP_PREFIX) && isIconName && isBitmap
    }

    private companion object {
        const val ORDER = 30
        const val ICON_LAUNCHER = "ic_launcher"
        const val ICON_LAUNCHER_ROUND = "ic_launcher_round"
        const val MIPMAP_PREFIX = "mipmap"
    }
}
