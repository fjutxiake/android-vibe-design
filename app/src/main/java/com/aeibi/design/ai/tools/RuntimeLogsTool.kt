package com.aeibi.design.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.aeibi.design.data.runtimelogs.RuntimeLogEntry
import com.aeibi.design.data.runtimelogs.RuntimeLogStore

/**
 * 预览运行时日志工具——把 WebView 的 console 反馈暴露给 agent。
 *
 * agent 修改代码后调用 [readRuntimeLogs] 检查运行时错误（浏览器级 lint），
 * 是"构建-验证-修复"闭环的反馈输入（issue #17 的 RuntimeFeedback 落地形态）。
 */
@LLMDescription("Tools for reading runtime console logs from the preview WebView.")
class RuntimeLogsTool(private val store: RuntimeLogStore) : ToolSet {

    @Tool(customName = "read_runtime_logs")
    @LLMDescription(
        "Read recent runtime console logs from the preview WebView. " +
            "Levels: ERROR (JS errors / load failures), WARNING, and INFO/LOG noise. " +
            "Call this after modifying code to check whether the page still runs correctly."
    )
    suspend fun readRuntimeLogs(
        @LLMDescription("Optional filter: ERROR, WARNING, or ALL (default).") level: String = "ALL"
    ): String {
        val filtered = when (level.uppercase()) {
            "ERROR" -> store.snapshot().filter { it.level == LEVEL_ERROR }
            "WARNING" -> store.snapshot().filter { it.level == LEVEL_WARNING }
            else -> store.snapshot()
        }
        return filtered.joinToString("\n") { entry -> formatEntry(entry) }
            .ifEmpty { "No runtime logs recorded." }
    }

    @Tool(customName = "clear_runtime_logs")
    @LLMDescription("Clear recorded runtime logs (e.g. before a fresh preview round).")
    suspend fun clearRuntimeLogs(): String {
        store.clear()
        return "Runtime logs cleared."
    }

    private fun formatEntry(entry: RuntimeLogEntry): String {
        val source = if (entry.source.isNotEmpty()) " (${entry.source})" else ""
        return "[${entry.level}] ${entry.message}$source"
    }

    private companion object {
        const val LEVEL_ERROR = "ERROR"
        const val LEVEL_WARNING = "WARNING"
    }
}
