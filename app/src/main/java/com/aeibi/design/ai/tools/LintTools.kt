package com.aeibi.design.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File
import org.jsoup.parser.Parser
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException

/**
 * 静态 lint 工具——工作区前端文件的成熟解析器检查（Jsoup/Rhino，非自研规则）。
 *
 * 定位：运行时 console 反馈（read_runtime_logs）是主要检查；本工具补充
 * 静态结构/语法层（HTML 解析错误与本地引用、JS 语法）。
 * 纯只读，不修改工作区。
 */
@LLMDescription("Static lint tools for workspace frontend files (HTML structure/references, JS syntax).")
class LintTools(private val workspaceRoot: File) : ToolSet {

    @Tool(customName = "lint_files")
    @LLMDescription(
        "Lint HTML and JS files in the workspace. HTML: parse errors and broken local references " +
            "(img/script/link/a src/href). JS: syntax errors with line numbers. " +
            "Call after writing code and before claiming completion."
    )
    suspend fun lintFiles(
        @LLMDescription(
            "Relative file or directory to lint, or '.' (default) for the whole workspace."
        ) path: String = "."
    ): String {
        val target = resolve(path)
        if (!target.exists()) return "ERROR: path not found: $path"
        val htmlFiles = filesUnder(target).filter { it.extension in HTML_EXTENSIONS }.toList().sortedBy { it.name }
        val jsFiles = filesUnder(target).filter { it.extension == "js" }.toList().sortedBy { it.name }

        val findings = mutableListOf<String>()
        htmlFiles.forEach { file -> findings += lintHtml(file) }
        jsFiles.forEach { file -> findings += lintJs(file) }

        return findings.joinToString("\n")
            .ifEmpty { "Lint OK (${htmlFiles.size} HTML, ${jsFiles.size} JS files checked)." }
    }

    /** HTML：Jsoup 解析错误 + 本地引用完整性（img/script/link/a 的 src/href）。 */
    private fun lintHtml(file: File): List<String> {
        val findings = mutableListOf<String>()
        val content = runCatching { file.readText() }.getOrElse { return emptyList() }
        val relative = file.relativeTo(workspaceRoot).invariantSeparatorsPath

        // 1. 解析错误（Jsoup 错误追踪）
        val parser = Parser.htmlParser().setTrackErrors(MAX_PARSE_ERRORS)
        val document = parser.parseInput(content, "")
        parser.errors.forEach { error ->
            findings += "$relative:${error.position}: HTML: ${error.errorMessage}"
        }

        // 2. 本地引用完整性（成熟解析器提取，替代手写正则）
        document.select("[src], [href]").forEach { element ->
            val attr = if (element.hasAttr("src")) "src" else "href"
            val ref = element.attr(attr)
            if (ref.isEmpty() ||
                ref.startsWith("http://") ||
                ref.startsWith("https://") ||
                ref.startsWith("//") ||
                ref.startsWith("data:") ||
                ref.startsWith("#") ||
                ref.startsWith("javascript:")
            ) {
                return@forEach
            }
            val resolved = file.parentFile.resolve(ref).normalize()
            if (!resolved.isFile) {
                findings += "$relative: $attr=$ref 引用不存在: ${resolved.relativeTo(workspaceRoot)}"
            }
        }
        return findings
    }

    /** JS：Rhino 语法编译检查（解释模式，捕获行号）。 */
    private fun lintJs(file: File): List<String> {
        val content = runCatching { file.readText() }.getOrElse { return emptyList() }
        val relative = file.relativeTo(workspaceRoot).invariantSeparatorsPath
        val cx = Context.enter()
        return try {
            cx.optimizationLevel = -1 // 解释模式（Android 兼容，不做 JIT 优化）
            cx.compileString(content, relative, 1, null)
            emptyList()
        } catch (error: EvaluatorException) {
            listOf("$relative:${error.lineNumber()}: JS: ${error.details()}")
        } catch (error: Exception) {
            listOf("$relative: JS: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            Context.exit()
        }
    }

    /** 相对路径解析（沙箱内校验）→ 文件或目录。 */
    private fun resolve(path: String): File {
        val target = workspaceRoot.resolve(path).normalize()
        check(target.toPath().startsWith(workspaceRoot.toPath())) { "Path escapes the workspace: $path" }
        return target
    }

    private fun filesUnder(target: File): Sequence<File> = if (target.isDirectory) {
        target.walkTopDown().filter { it.isFile }
    } else {
        sequenceOf(target)
    }

    private companion object {
        const val MAX_PARSE_ERRORS = 20
        val HTML_EXTENSIONS = setOf("html", "htm")
    }
}
