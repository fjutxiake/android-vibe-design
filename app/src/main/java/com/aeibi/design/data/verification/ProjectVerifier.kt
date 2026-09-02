package com.aeibi.design.data.verification

import com.aeibi.design.feature.workspace.WorkspaceConfig
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 项目验证器——CI 模型的本地执行器（issue #17 方向的最小实现）。
 *
 * 对工作区运行内置机械检查并产出 [VerifyReport]。
 * 后续扩展点：
 * - 项目规则挂载（规则随项目走）
 * - 预览运行时反馈合入（JS console / 加载错误——见 feature/preview-feedback）
 * - agent 循环调用（构建后自动验证）
 */
class ProjectVerifier(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun verify(workspace: File): VerifyReport {
        val results = mutableListOf<CheckResult>()

        // 入口检查：vibe.config.json 的 preview.entry（无配置默认 index.html）
        val configFile = File(workspace, CONFIG_FILE)
        val entry: String
        if (configFile.isFile) {
            val config = runCatching { json.decodeFromString<WorkspaceConfig>(configFile.readText()) }
                .getOrElse { error ->
                    results += CheckResult(
                        CHECK_CONFIG,
                        CheckSeverity.ERROR,
                        "vibe.config.json 解析失败: ${error.message}"
                    )
                    WorkspaceConfig()
                }
            entry = config.preview.entry
            results += BuiltinChecks.entryExists(workspace, entry, "vibe.config.json")
        } else {
            entry = DEFAULT_ENTRY
            results += BuiltinChecks.entryExists(workspace, entry, "默认")
        }

        results += BuiltinChecks.localRefsResolve(workspace)

        return VerifyReport(workspace.absolutePath, results)
    }

    private companion object {
        const val CONFIG_FILE = "vibe.config.json"
        const val DEFAULT_ENTRY = "index.html"
        const val CHECK_CONFIG = "config-well-formed"
    }
}
