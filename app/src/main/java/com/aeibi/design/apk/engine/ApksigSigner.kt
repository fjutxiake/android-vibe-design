package com.aeibi.design.apk.engine

import com.android.apksig.ApkSigner as AndroidApkSigner
import java.nio.file.Path
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * apksig 签名实现（Google 官方库，纯 Java，Android 可用）。
 *
 * 密钥从 [keyStorePath]（PKCS12）读取；密钥由 [RuntimeKeystoreProvider] 生成/持久化。
 */
class ApksigSigner(private val keyStorePath: Path, private val alias: String, private val storePass: CharArray) :
    ApkSigner {

    override fun sign(input: Path, output: Path, key: SigningKey) {
        val (privateKey, certs) = loadKey(key)
        val signerConfig =
            AndroidApkSigner.SignerConfig.Builder(KEY_ALGORITHM, privateKey, listOf(certs)).build()
        val builder =
            AndroidApkSigner.Builder(listOf(signerConfig))
                .setInputApk(input.toFile())
                .setOutputApk(output.toFile())
                .setV1SigningEnabled(true) // 兼容旧系统（v1 对 minSdk<24 或部分 ROM 必需）
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setAlignFileSize(true) // ZIP 条目 4 字节对齐：Android 7 上 v2 签名安装的硬性要求
        builder.build().sign()
    }

    private fun loadKey(key: SigningKey): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            keyStorePath.toFile().inputStream().use { load(it, key.storePass.toCharArray()) }
        }
        val entry = keyStore.getEntry(key.alias, KeyStore.PasswordProtection(key.keyPass.toCharArray()))
            ?: throw KeyStoreException("密钥不存在: ${key.alias}")
        if (entry !is KeyStore.PrivateKeyEntry) {
            throw KeyStoreException("密钥条目类型错误: ${key.alias}")
        }
        return entry.privateKey to (entry.certificate as X509Certificate)
    }

    private companion object {
        const val KEYSTORE_TYPE = "PKCS12"
        const val KEY_ALGORITHM = "SHA256withRSA"
    }
}
