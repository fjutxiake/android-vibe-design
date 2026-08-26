package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkBuilderEngine
import com.aeibi.design.apk.engine.ApkDecoder
import com.aeibi.design.apk.engine.ApkLayout
import com.aeibi.design.apk.engine.ApkSigner
import com.aeibi.design.apk.engine.NoSigningKeyProvider
import com.aeibi.design.apk.engine.SigningKeyProvider
import com.aeibi.design.apk.engine.Zipaligner
import com.aeibi.design.apk.model.ApkBuildRequest
import com.aeibi.design.apk.model.ApkBuildResult
import com.aeibi.design.apk.verify.ApkVerifier
import com.aeibi.design.apk.verify.VerificationReport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * APK 构建管线门面：decode → 明文操作 → build → 二进制操作 → align → sign → verify。
 *
 * 扩展方式（全部为可插拔点）：
 * - 新增明文修改项：实现 [ApkOperation] 加入 [operations]
 * - 新增二进制修改项：实现 [ApkBinaryOperation] 加入 [binaryOperations]
 * - 换引擎：换 [ApkDecoder]/[ApkBuilderEngine]/[Zipaligner]/[ApkSigner] 实现 + 配套 [ApkLayout]
 * - 换签名来源：换 [SigningKeyProvider]（可对接 SecureStore）
 * - 构建后验证：提供 [ApkVerifier]（验证契约钩子）
 * - 日志消费：换 [BuildLogger]（可对接 UI/持久化）
 */
class ApkPipeline(
    private val decoder: ApkDecoder,
    private val builder: ApkBuilderEngine,
    private val zipaligner: Zipaligner,
    private val signer: ApkSigner,
    private val layout: ApkLayout,
    private val signingKeyProvider: SigningKeyProvider = NoSigningKeyProvider,
    private val operations: List<ApkOperation> = emptyList(),
    private val binaryOperations: List<ApkBinaryOperation> = emptyList(),
    private val verifier: ApkVerifier? = null,
    private val logger: BuildLogger = PrintBuildLogger,
    private val workDir: Path = ApkIo.createTempDir("apk-build")
) {
    /** 使用构造器注入的 logger 构建。 */
    fun build(request: ApkBuildRequest): ApkBuildResult = build(request, logger)

    /** 使用指定 logger 构建（调用方可按需提供日志消费，如临时调试 UI）。 */
    fun build(request: ApkBuildRequest, logger: BuildLogger): ApkBuildResult {
        require(Files.exists(request.templateApk)) { "模板 APK 不存在: ${request.templateApk}" }
        val startedAt = System.currentTimeMillis()
        val executed = mutableListOf<String>()
        val decodedDir = workDir.resolve("decoded")

        try {
            logger.log(BuildStage.DECODE, "解码模板: ${request.templateApk.fileName}")
            decoder.decode(request.templateApk, decodedDir)

            operations.forEach { operation ->
                val context = ApkOperationContext(
                    decodedDir = decodedDir,
                    layout = layout,
                    request = request,
                    log = { logger.log(BuildStage.OPERATION, "$operation.name: $it") }
                )
                logger.log(BuildStage.OPERATION, "执行: ${operation.name}")
                operation.apply(context)
                executed += operation.name
            }

            var apkFile = workDir.resolve("rebuilt.apk")
            logger.log(BuildStage.BUILD, "重打包")
            builder.build(decodedDir, apkFile)

            binaryOperations.forEachIndexed { index, operation ->
                val out = workDir.resolve("binary-$index.apk")
                val context = ApkBinaryOperationContext(
                    apkFile = apkFile,
                    outputFile = out,
                    request = request,
                    log = { logger.log(BuildStage.BINARY_OPERATION, "$operation.name: $it") }
                )
                logger.log(BuildStage.BINARY_OPERATION, "执行: ${operation.name}")
                operation.apply(context)
                apkFile = out
                executed += operation.name
            }

            val aligned = workDir.resolve("aligned.apk")
            logger.log(BuildStage.ALIGN, "对齐")
            zipaligner.align(apkFile, aligned)

            val signed = signingKeyProvider.provideKey()?.let { key ->
                logger.log(BuildStage.SIGN, "签名")
                val out = workDir.resolve("signed.apk")
                signer.sign(aligned, out, key)
                out
            } ?: aligned

            val verification = verifier?.let {
                logger.log(BuildStage.VERIFY, "验证")
                it.verify(signed)
            } ?: VerificationReport.passed()

            Files.copy(signed, request.outputApk, StandardCopyOption.REPLACE_EXISTING)
            logger.log(BuildStage.COMPLETE, "完成: ${request.outputApk}")
            return ApkBuildResult(
                outputApk = request.outputApk,
                operationsExecuted = executed,
                durationMs = System.currentTimeMillis() - startedAt,
                verification = verification
            )
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }
}
