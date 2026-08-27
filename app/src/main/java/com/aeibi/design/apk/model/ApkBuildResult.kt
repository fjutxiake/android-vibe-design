package com.aeibi.design.apk.model

import com.aeibi.design.apk.engine.BuildSummary
import com.aeibi.design.apk.verify.VerificationReport
import java.nio.file.Path

/**
 * 一次 APK 构建的结果。
 *
 * [operationsExecuted] 按执行顺序记录每步操作名称，
 * 供 UI 展示"构建日志"与未来的诚实报告（✅ 已执行 / 跳过）。
 */
data class ApkBuildResult(
    /** 最终产出 APK 路径（签名后）。 */
    val outputApk: Path,
    /** 实际执行的操作名称列表（未触发的操作不在此列）。 */
    val operationsExecuted: List<String>,
    /** 构建产物自检摘要（dex/前端/体积——机械验证的数据源）。 */
    val buildSummary: BuildSummary,
    /** 构建后验证报告（未提供验证器时为通过空报告）。 */
    val verification: VerificationReport,
    /** 构建耗时（毫秒）。 */
    val durationMs: Long
)
