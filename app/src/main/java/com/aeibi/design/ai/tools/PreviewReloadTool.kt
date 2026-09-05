package com.aeibi.design.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * 预览刷新工具——让 agent 主动加载/刷新预览页面。
 *
 * WebView 只在页面 reload 后才会产生新的运行时日志——agent 修改文件后调用本工具
 * 刷新页面，再调 read_runtime_logs 才能拿到当前代码的运行结果（闭环验证）。
 * 实际 reload 由 UI 层执行（WebView 不属于 agent 沙箱），本工具只发出请求。
 */
@LLMDescription("Tool for requesting the preview page to reload.")
class PreviewReloadTool(private val onReloadRequested: () -> Unit) : ToolSet {

    @Tool(customName = "reload_preview")
    @LLMDescription(
        "Reload the preview page so it runs the current workspace files. " +
            "Call this after modifying files and before reading runtime logs — " +
            "logs only reflect the page state after a reload."
    )
    suspend fun reloadPreview(): String {
        onReloadRequested()
        return "Preview reload requested. Wait a moment, then call read_runtime_logs to check the current state."
    }
}
