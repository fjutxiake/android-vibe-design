package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * 修改包名（applicationId）。
 *
 * 改动范围（三类，缺一不可）：
 * 1. manifest 根元素的 package 属性
 * 2. permission / uses-permission 的 android:name（包名前缀声明，不改会与
 *    已安装的旧包冲突：INSTALL_FAILED_DUPLICATE_PERMISSION）
 * 3. provider 的 android:authorities（包名前缀）
 *
 * 组件类名（android:name 指向 dex 类）保持模板原样——dex 类未被修改，
 * 类名改了会导致运行时 ClassNotFoundException。壳场景中
 * applicationId 与组件类名分属不同包是合法且常见的做法。
 */
class PackageNameOperation : ApkOperation {

    override val name: String = "修改包名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val newPackage = context.request.packageName ?: return
        require(newPackage.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) {
            "包名格式不合法: $newPackage"
        }

        val manifestFile = context.layout.manifestFile(context.decodedDir)
        require(Files.exists(manifestFile)) { "解码产物缺少 AndroidManifest.xml" }

        val oldPackage = extractPackage(manifestFile)
        val content = ApkIo.readString(manifestFile)
        val updated =
            content
                .replace(Regex("""package="[^"]+""""), """package="$newPackage"""")
                .replace(
                    Regex("""<(permission|uses-permission)\s+android:name="$oldPackage\.([^"]+)""""),
                    "<\$1 android:name=\"$newPackage.\$2\""
                )
                .replace(
                    Regex("""android:authorities="$oldPackage\.([^"]+)""""),
                    "android:authorities=\"$newPackage.\$1\""
                )
        ApkIo.writeString(manifestFile, updated)

        context.log("包名: $oldPackage → $newPackage（权限/authority 跟随，组件类名保持模板）")
    }

    private fun extractPackage(manifestFile: Path): String {
        val content = ApkIo.readString(manifestFile)
        val match = Regex("""package="([^"]+)"""").find(content)
            ?: error("manifest 缺少 package 属性")
        return match.groupValues[1]
    }

    private companion object {
        const val ORDER = 10
    }
}
