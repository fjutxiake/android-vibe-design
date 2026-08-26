package com.aeibi.design.apk.engine

import java.nio.file.Path

/**
 * 签名密钥来源抽象——签名环节的扩展点。
 *
 * 当前默认实现为文件 keystore（[FileSigningKeyProvider]）；
 * 后续可接入 Android Keystore / SecureStore 加密存储，管线无需改动。
 */
fun interface SigningKeyProvider {

    /** 提供签名密钥；返回 null 表示跳过签名。 */
    fun provideKey(): SigningKey?
}

/** 文件级 keystore 的实现（桌面验证/开发环境用）。 */
class FileSigningKeyProvider(
    private val keystorePath: Path,
    private val alias: String,
    private val storePass: String,
    private val keyPass: String
) : SigningKeyProvider {

    override fun provideKey(): SigningKey =
        SigningKey(keystorePath = keystorePath, alias = alias, storePass = storePass, keyPass = keyPass)
}

/** 永不签名的默认实现。 */
object NoSigningKeyProvider : SigningKeyProvider {
    override fun provideKey(): SigningKey? = null
}
