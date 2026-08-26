package com.aeibi.design.apk

import com.aeibi.design.apk.model.ApkBuildRequest
import java.nio.file.Path

/**
 * 二进制级 APK 操作接口——为引擎二进制能力预留的扩展点。
 *
 * 与 [com.aeibi.design.apk.ApkOperation]（作用于解码明文目录）不同，
 * 本接口作用于已重建的 APK 文件本身，面向无法用明文表达的结构修改：
 * - 资源混淆保护（protect）
 * - 资源名重构（refactor）
 * - split APK 合并（merge）
 * - 定点字符串池修改
 *
 * MVP 阶段无需实现；接口先行，未来接入 ARSCLib 二进制能力时管线无需改动。
 */
interface ApkBinaryOperation {

    /** 操作的人类可读名称，用于构建日志与结果汇报。 */
    val name: String

    /** 对已重建的 APK 执行修改。 */
    fun apply(context: ApkBinaryOperationContext)
}

/**
 * 二进制操作执行上下文。
 *
 * @param apkFile    输入 APK（管线重建后的产物）
 * @param outputFile 输出 APK 路径（操作后的结果，供下一环节使用）
 * @param request    本次构建请求
 * @param log        构建日志回调
 */
class ApkBinaryOperationContext(
    val apkFile: Path,
    val outputFile: Path,
    val request: ApkBuildRequest,
    val log: (String) -> Unit
)
