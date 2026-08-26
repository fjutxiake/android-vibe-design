package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkBuilderEngine
import com.aeibi.design.apk.engine.ApkDecoder
import com.aeibi.design.apk.engine.ApkLayout
import com.aeibi.design.apk.engine.ApkSigner
import com.aeibi.design.apk.engine.FileSigningKeyProvider
import com.aeibi.design.apk.engine.SigningKey
import com.aeibi.design.apk.engine.Zipaligner
import com.aeibi.design.apk.model.ApkBuildRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkPipelineTest {

    private class FakeEngine :
        ApkDecoder,
        ApkBuilderEngine,
        Zipaligner,
        ApkSigner {

        val calls = mutableListOf<String>()

        override fun decode(sourceApk: Path, destDir: Path) {
            calls += "decode"
            Files.createDirectories(destDir)
            Files.writeString(destDir.resolve("AndroidManifest.xml"), "<manifest/>")
        }

        override fun build(decodedDir: Path, outApk: Path) {
            calls += "build"
            Files.writeString(outApk, "built")
        }

        override fun align(input: Path, output: Path, alignment: Int) {
            calls += "align"
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
        }

        override fun sign(input: Path, output: Path, key: SigningKey) {
            calls += "sign"
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class FakeLayout : ApkLayout {
        override fun manifestFile(decodedDir: Path): Path = decodedDir.resolve("AndroidManifest.xml")

        override fun resRoot(decodedDir: Path): Path = decodedDir.resolve("res")

        override fun rootDir(decodedDir: Path): Path = decodedDir.resolve("root")
    }

    private class LoggingOperation(private val log: (String) -> Unit) : ApkOperation {
        override val name: String = "测试操作"

        override val order: Int = 0

        override fun apply(context: ApkOperationContext) {
            log("操作执行")
        }
    }

    private class LoggingBinaryOperation(private val log: (String) -> Unit) : ApkBinaryOperation {
        override val name: String = "二进制测试操作"

        override fun apply(context: ApkBinaryOperationContext) {
            log("二进制操作执行")
            Files.copy(context.apkFile, context.outputFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Test
    fun `管线按 decode-操作-build-二进制操作-align-sign 顺序执行`() {
        val engine = FakeEngine()
        val logs = mutableListOf<String>()
        val workDir = Files.createTempDirectory("pipeline")
        val template = workDir.resolve("shell.apk")
        Files.writeString(template, "template")
        val output = workDir.resolve("out.apk")
        val pipeline = ApkPipeline(
            decoder = engine,
            builder = engine,
            zipaligner = engine,
            signer = engine,
            layout = FakeLayout(),
            signingKeyProvider = FileSigningKeyProvider(workDir.resolve("k.keystore"), "alias", "pass", "pass"),
            operations = listOf(LoggingOperation(logs::add)),
            binaryOperations = listOf(LoggingBinaryOperation(logs::add)),
            logger = BuildLogger { _, message -> logs.add(message) },
            workDir = workDir.resolve("work")
        )

        val result = pipeline.build(ApkBuildRequest(templateApk = template, outputApk = output))

        assertEquals(listOf("decode", "build", "align", "sign"), engine.calls)
        assertEquals(listOf("测试操作", "二进制测试操作"), result.operationsExecuted)
        assertTrue(Files.exists(output))
        assertTrue(result.verification.passed)
    }

    @Test
    fun `无签名密钥时跳过签名步骤`() {
        val engine = FakeEngine()
        val workDir = Files.createTempDirectory("pipeline-nokey")
        val template = workDir.resolve("shell.apk")
        Files.writeString(template, "template")
        val pipeline = ApkPipeline(
            decoder = engine,
            builder = engine,
            zipaligner = engine,
            signer = engine,
            layout = FakeLayout(),
            workDir = workDir.resolve("work")
        )

        pipeline.build(ApkBuildRequest(templateApk = template, outputApk = workDir.resolve("out.apk")))

        assertEquals(listOf("decode", "build", "align"), engine.calls)
    }
}
