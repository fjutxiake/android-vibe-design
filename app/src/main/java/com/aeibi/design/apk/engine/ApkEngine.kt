package com.aeibi.design.apk.engine

import java.nio.file.Path

/**
 * APK 构建引擎 SPI——替换点。
 *
 * 当前实现为桌面验证版（CLI 调用 APKEditor），
 * 后续接入手机端时替换为 ARSCLib/APKEditor 的纯 Java 移植实现即可，
 * 管线与操作层无需改动。
 */

/** 解码：把模板 APK 解码为明文目录（manifest/资源为可读 XML）。 */
interface ApkDecoder {
    fun decode(sourceApk: Path, destDir: Path)
}

/** 重打包：把明文目录构建回 APK，返回产物自检信息。 */
interface ApkBuilderEngine {
    fun build(decodedDir: Path, outApk: Path): BuildSummary
}

/**
 * 构建产物摘要（自检信息）——上层可据此做机械验证：
 * 产物是否包含 dex、前端注入是否生效、体积是否合理。
 */
data class BuildSummary(
    /** 产物大小（字节）。 */
    val apkSizeBytes: Long,
    /** ZIP 条目总数。 */
    val entryCount: Int,
    /** 是否包含 dex。 */
    val hasDex: Boolean,
    /** dex 文件数。 */
    val dexCount: Int,
    /** 是否包含前端注入目录（assets/frontend_app/）。 */
    val hasFrontendAssets: Boolean,
    /** 前端文件数（assets/frontend_app/ 下）。 */
    val frontendFileCount: Int
)

/** 对齐：zipalign。 */
interface Zipaligner {
    fun align(input: Path, output: Path, alignment: Int = 4)
}

/** 签名密钥（文件级 keystore；后续可对接 SecureStore 加密存储）。 */
data class SigningKey(val keystorePath: Path, val alias: String, val storePass: String, val keyPass: String)

/** 签名：v1/v2/v3。 */
interface ApkSigner {
    fun sign(input: Path, output: Path, key: SigningKey)
}

/**
 * 不对齐实现（临时）：当前签名器（apksig）已在签名时执行对齐
 * （setAlignFileSize），此实现仅作占位；如换用不对齐的签名器需替换为真对齐实现。
 */
object NoOpZipaligner : Zipaligner {
    override fun align(input: Path, output: Path, alignment: Int) {
        java.nio.file.Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
