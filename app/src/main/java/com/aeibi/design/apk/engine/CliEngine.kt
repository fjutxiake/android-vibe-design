package com.aeibi.design.apk.engine

import java.io.File
import java.nio.file.Path

/**
 * 桌面验证版引擎：通过命令行调用 APKEditor（解码/重打包）与 build-tools（zipalign/apksigner）。
 *
 * 仅用于开发环境验证 APK 手术链路；手机端版本应替换为
 * ARSCLib/APKEditor 的纯 Java 移植实现（见 docs 设计）。
 */
class CliEngine(
    private val javaBin: Path,
    private val apkEditorJar: Path,
    private val apkEditorLibs: Path,
    private val zipalignBin: Path,
    private val apksignerJar: Path
) : ApkDecoder,
    ApkBuilderEngine,
    Zipaligner,
    ApkSigner {

    override fun decode(sourceApk: Path, destDir: Path) {
        exec(
            javaBin,
            "-cp", classpath(),
            "com.reandroid.apkeditor.Main", "d",
            "-i", sourceApk.toString(),
            "-o", destDir.toString(),
            "-t", "xml"
        )
    }

    override fun build(decodedDir: Path, outApk: Path) {
        exec(
            javaBin,
            "-cp", classpath(),
            "com.reandroid.apkeditor.Main", "b",
            "-i", decodedDir.toString(),
            "-o", outApk.toString()
        )
    }

    override fun align(input: Path, output: Path, alignment: Int) {
        exec(zipalignBin, "-f", alignment.toString(), input.toString(), output.toString())
    }

    override fun sign(input: Path, output: Path, key: SigningKey) {
        exec(
            javaBin,
            "-jar", apksignerJar.toString(),
            "sign",
            "--ks", key.keystorePath.toString(),
            "--ks-key-alias", key.alias,
            "--ks-pass", "pass:${key.storePass}",
            "--key-pass", "pass:${key.keyPass}",
            "--out", output.toString(),
            input.toString()
        )
    }

    private fun classpath(): String = "$apkEditorJar${File.pathSeparator}$apkEditorLibs${File.pathSeparator}*"

    private fun exec(vararg command: Any) {
        val process = ProcessBuilder(command.map(Any::toString))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw IllegalStateException("CLI 引擎执行失败（exit ${process.exitValue()}）:\n$output")
        }
    }
}
