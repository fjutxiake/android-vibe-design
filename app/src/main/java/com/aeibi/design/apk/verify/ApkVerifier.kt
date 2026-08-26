package com.aeibi.design.apk.verify

import java.nio.file.Path

/**
 * 构建后验证接口——验证契约（Verification Contract）的钩子。
 *
 * 对应需求文档中的验证强度分级：
 * - 第一级（必须）：能跑、能点、不崩（运行时检查）
 * - 第二级（关键需求）：AC 清单 + 静态扫描
 * - 第三级（导出时）：完整 lint + 已知债清单
 *
 * MVP 阶段可先提供基础实现（如：产物存在、签名有效、包名一致）；
 * 后续接入静态扫描/运行时检查时管线无需改动。
 */
fun interface ApkVerifier {

    /** 对构建产物执行验证。 */
    fun verify(apk: Path): VerificationReport
}

/** 验证结果报告（供 UI 展示与"诚实报告"使用）。 */
data class VerificationReport(
    /** 是否全部通过。 */
    val passed: Boolean,
    /** 问题清单（未通过时的原因；通过时可为空）。 */
    val issues: List<String>
) {
    companion object {
        fun passed(): VerificationReport = VerificationReport(passed = true, issues = emptyList())

        fun failed(vararg issues: String): VerificationReport =
            VerificationReport(passed = false, issues = issues.toList())
    }
}
