package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import com.aeibi.design.apk.tool.ToolArgs
import com.aeibi.design.apk.tool.ToolContext

/**
 * 修改包名（applicationId）——工具编排示范：本操作只含业务逻辑，
 * 文件操作全部通过工具（read_file / edit_file）完成。
 *
 * 改动范围（三类，缺一不可）：
 * 1. manifest 根元素的 package 属性
 * 2. permission / uses-permission 的 android:name（包名前缀声明，不改会与
 *    已安装的旧包冲突：INSTALL_FAILED_DUPLICATE_PERMISSION）
 * 3. provider 的 android:authorities（包名前缀）
 *
 * 组件类名（android:name 指向 dex 类）保持模板原样——dex 类未被修改，
 * 类名改了会导致运行时 ClassNotFoundException。
 */
class PackageNameOperation : ApkOperation {

    override val name: String = "修改包名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val newPackage = context.request.packageName ?: return
        require(newPackage.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) {
            "包名格式不合法: $newPackage"
        }

        val toolContext = ToolContext(decodedDir = context.decodedDir, log = context.log)
        val manifestPath = manifestRelativePath(context)

        // 1. read_file：读取 manifest，提取旧包名
        val oldPackage = readManifestPackage(context, toolContext, manifestPath)

        // 2. edit_file × N：分别替换 package 属性 / 权限声明 / authorities
        edit(context, toolContext, manifestPath, "package=\"$oldPackage\"", "package=\"$newPackage\"")

        val current = readAgain(context, toolContext, manifestPath)
        replaceAll(
            context,
            toolContext,
            manifestPath,
            current,
            "<permission android:name=\"$oldPackage.",
            "<permission android:name=\"$newPackage."
        )
        replaceAll(
            context,
            toolContext,
            manifestPath,
            current,
            "<uses-permission android:name=\"$oldPackage.",
            "<uses-permission android:name=\"$newPackage."
        )
        replaceAll(
            context,
            toolContext,
            manifestPath,
            current,
            "android:authorities=\"$oldPackage.",
            "android:authorities=\"$newPackage."
        )

        context.log("包名: $oldPackage → $newPackage（权限/authority 跟随，组件类名保持模板）")
    }

    private fun readManifestPackage(context: ApkOperationContext, toolContext: ToolContext, path: String): String {
        val read = executeRead(context, toolContext, path)
        val content = read.data?.get("content") as? String ?: error("read_file 返回缺少内容")
        return Regex("""package="([^"]+)"""").find(content)
            ?.groupValues
            ?.get(1)
            ?: error("manifest 缺少 package 属性")
    }

    private fun readAgain(context: ApkOperationContext, toolContext: ToolContext, path: String): String {
        val read = executeRead(context, toolContext, path)
        return read.data?.get("content") as? String ?: ""
    }

    private fun executeRead(context: ApkOperationContext, toolContext: ToolContext, path: String) =
        context.tools.execute("read_file", toolContext, ToolArgs(path = path))
            ?.also {
                if (!it.success) error("读取失败: ${it.message}")
            }
            ?: error("工具 read_file 未注册")

    private fun edit(
        context: ApkOperationContext,
        toolContext: ToolContext,
        path: String,
        oldText: String,
        newText: String
    ) {
        val result =
            context.tools.execute("edit_file", toolContext, ToolArgs(path = path, oldText = oldText, newText = newText))
                ?: error("工具 edit_file 未注册")
        if (!result.success) error("替换失败（$oldText）: ${result.message}")
    }

    private fun replaceAll(
        context: ApkOperationContext,
        toolContext: ToolContext,
        path: String,
        current: String,
        oldText: String,
        newText: String
    ) {
        if (current.contains(oldText)) {
            edit(context, toolContext, path, oldText, newText)
        }
    }

    private fun manifestRelativePath(context: ApkOperationContext): String =
        context.decodedDir.relativize(context.layout.manifestFile(context.decodedDir)).toString()

    private companion object {
        const val ORDER = 10
    }
}
