package com.aeibi.design.data.verification

import java.io.File

/**
 * 内置机械检查集（CI 模型的官方 actions 对应物）——纯文件逻辑，JVM 可测。
 * 检查集固定可枚举，不随需求膨胀（业务语义交给预览与人工判断）。
 */
object BuiltinChecks {

    private val REF_PATTERN = Regex("""(?:src|href)\s*=\s*["']([^"'#?]+)["']""")

    /** 入口存在性：vibe.config.json 的 preview.entry（默认 index.html）引用的文件存在。 */
    fun entryExists(workspace: File, entry: String, entrySource: String): CheckResult {
        val entryFile = workspace.resolve(entry).normalize()
        return if (entryFile.isFile) {
            CheckResult(CHECK_ENTRY, CheckSeverity.OK, "入口存在: $entry（$entrySource）")
        } else {
            CheckResult(CHECK_ENTRY, CheckSeverity.ERROR, "入口缺失: $entry（$entrySource）")
        }
    }

    /** 本地引用完整性：HTML 中 src/href 引用的本地相对文件存在（忽略外链/锚点/数据 URI）。 */
    fun localRefsResolve(workspace: File): CheckResult {
        val htmlFiles = workspace.walkTopDown()
            .filter { it.isFile && it.extension in HTML_EXTENSIONS }
            .toList()
        if (htmlFiles.isEmpty()) {
            return CheckResult(CHECK_REFS, CheckSeverity.WARNING, "未找到 HTML 文件，跳过引用检查")
        }

        val broken = mutableListOf<String>()
        htmlFiles.forEach { html ->
            val base = html.parentFile
            REF_PATTERN.findAll(html.readText()).forEach { match ->
                val ref = match.groupValues[1]
                if (ref.startsWith("//") || ref.startsWith("http://") || ref.startsWith("https://")) return@forEach
                if (ref.startsWith("data:")) return@forEach
                if (ref.isEmpty()) return@forEach
                if (!base.resolve(ref).normalize().isFile) {
                    broken += "${html.relativeTo(workspace)} → $ref"
                }
            }
        }
        return if (broken.isEmpty()) {
            CheckResult(CHECK_REFS, CheckSeverity.OK, "本地引用完整（${htmlFiles.size} 个 HTML）")
        } else {
            CheckResult(CHECK_REFS, CheckSeverity.ERROR, "断链 ${broken.size} 处:\n${broken.take(5).joinToString("\n")}")
        }
    }

    private const val CHECK_ENTRY = "entry-exists"
    private const val CHECK_REFS = "local-refs"

    private val HTML_EXTENSIONS = setOf("html", "htm")
}
