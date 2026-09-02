package com.aeibi.design.data.verification

/** 检查严重度：✅ 通过 / ⚠️ 警告 / ❌ 错误（任一 ERROR 即验证失败）。 */
enum class CheckSeverity(val symbol: String) {
    OK("✅"),
    WARNING("⚠️"),
    ERROR("❌")
}

/** 单项检查结果。 */
data class CheckResult(val id: String, val severity: CheckSeverity, val message: String)

/**
 * 验证报告——CI 模型（issue #17）的本地验证输出。
 * 未来数据源：内置机械检查 + 项目规则 + 预览运行时反馈（console/加载错误）。
 */
data class VerifyReport(val workspacePath: String, val results: List<CheckResult>) {
    val passed: Boolean get() = results.none { it.severity == CheckSeverity.ERROR }

    fun summary(): String = results.joinToString(" ") { "${it.severity.symbol} ${it.id}" }
}
