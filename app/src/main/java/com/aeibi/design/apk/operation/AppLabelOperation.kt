package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext
import java.nio.file.Path

/**
 * 修改应用名：替换 resources/.../values/strings.xml 中的 app_name。
 */
class AppLabelOperation : ApkOperation {

    override val name: String = "修改应用名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val label = context.request.appLabel ?: return
        require(label.isNotBlank()) { "应用名不能为空" }

        val stringsFile = findStringsXml(context.decodedDir, context) ?: return
        val content = ApkIo.readString(stringsFile)
        val updated =
            content.replace(
                Regex("""<string\s+name="app_name"\s*>.*?</string>"""),
                """<string name="app_name">$label</string>"""
            )
        if (updated != content) {
            ApkIo.writeString(stringsFile, updated)
            context.log("应用名: $label")
        }
    }

    private fun findStringsXml(decodedDir: Path, context: ApkOperationContext): Path? =
        ApkIo.walk(context.layout.resRoot(decodedDir))
            .filter { it.fileName.toString() == "strings.xml" }
            .filter { ApkIo.readString(it).contains("app_name") }
            .firstOrNull()

    private companion object {
        const val ORDER = 20
    }
}
