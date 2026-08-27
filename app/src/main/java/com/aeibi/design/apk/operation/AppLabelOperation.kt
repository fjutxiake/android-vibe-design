package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import com.aeibi.design.apk.tool.ToolArgs
import com.aeibi.design.apk.tool.ToolContext

/**
 * 修改应用名——工具编排示范：read_file 定位 strings.xml，edit_file 替换 app_name。
 */
class AppLabelOperation : ApkOperation {

    override val name: String = "修改应用名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val label = context.request.appLabel ?: return
        require(label.isNotBlank()) { "应用名不能为空" }

        val stringsPath = findStringsRelativePath(context) ?: return
        val toolContext = ToolContext(decodedDir = context.decodedDir, log = context.log)

        // read_file：读取 strings.xml
        val read = context.tools.execute("read_file", toolContext, ToolArgs(path = stringsPath))
            ?: error("工具 read_file 未注册")
        if (!read.success) return
        val content = read.data?.get("content") as? String ?: return

        // 提取旧 app_name 值
        val oldLabel = Regex("""<string\s+name="app_name"\s*>([^<]*)</string>""")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?: return

        // edit_file：替换 app_name
        val result = context.tools.execute(
            "edit_file",
            toolContext,
            ToolArgs(
                path = stringsPath,
                oldText = "<string name=\"app_name\">$oldLabel</string>",
                newText = "<string name=\"app_name\">$label</string>"
            )
        ) ?: error("工具 edit_file 未注册")
        if (result.success) {
            context.log("应用名: $label")
        }
    }

    private fun findStringsRelativePath(context: ApkOperationContext): String? =
        ApkIo.walk(context.layout.resRoot(context.decodedDir))
            .filter { it.fileName.toString() == "strings.xml" }
            .filter { ApkIo.readString(it).contains("app_name") }
            .firstOrNull()
            ?.let { context.decodedDir.relativize(it).toString() }

    private companion object {
        const val ORDER = 20
    }
}
