package com.aeibi.design.apk

/**
 * 构建阶段——结构化日志的阶段标记。
 */
enum class BuildStage {
    DECODE,
    OPERATION,
    BINARY_OPERATION,
    BUILD,
    ALIGN,
    SIGN,
    VERIFY,
    COMPLETE
}

/**
 * 构建日志接口——为 UI 构建日志与"诚实报告"（已验证/已知简化/未验证）预留的扩展点。
 *
 * 默认实现 [PrintBuildLogger] 输出到标准输出（开发环境）；
 * 后续接入 Compose UI / 持久化日志时替换实现即可，管线无需改动。
 */
fun interface BuildLogger {

    /** 记录一条带阶段的构建日志。 */
    fun log(stage: BuildStage, message: String)
}

/** 输出到标准输出的默认实现。 */
object PrintBuildLogger : BuildLogger {
    override fun log(stage: BuildStage, message: String) {
        println("[$stage] $message")
    }
}
