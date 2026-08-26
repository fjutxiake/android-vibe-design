package com.aeibi.design.apk.engine

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.x509.X509V3CertificateGenerator

/**
 * 运行时签名密钥：首次生成 RSA 密钥对 + 自签名证书，持久化为 PKCS12 文件。
 *
 * 临时测试用；正式版应接入 Android Keystore / SecureStore。
 */
class RuntimeKeystoreProvider(
    private val keyStoreFile: File,
    private val alias: String = DEFAULT_ALIAS,
    private val storePass: CharArray = DEFAULT_PASS,
    private val keyPass: CharArray = DEFAULT_PASS
) : SigningKeyProvider {

    override fun provideKey(): SigningKey {
        ensureKeyStore()
        return SigningKey(
            keystorePath = keyStoreFile.toPath(),
            alias = alias,
            storePass = String(storePass),
            keyPass = String(keyPass)
        )
    }

    private fun ensureKeyStore() {
        if (keyStoreFile.exists()) return
        keyStoreFile.parentFile?.mkdirs()

        val keyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSignedCertificate(keyPair)

        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, storePass) }
        keyStore.setKeyEntry(
            alias,
            keyPair.private,
            keyPass,
            arrayOf(cert)
        )
        keyStoreFile.outputStream().use { keyStore.store(it, storePass) }
    }

    private fun selfSignedCertificate(keyPair: java.security.KeyPair): X509Certificate {
        val generator = X509V3CertificateGenerator()
        val now = Date.from(Instant.now())
        generator.setSerialNumber(BigInteger(160, SecureRandom()))
        generator.setSubjectDN(X500Principal("CN=Vibe Design Test"))
        generator.setIssuerDN(X500Principal("CN=Vibe Design Test"))
        generator.setNotBefore(now)
        generator.setNotAfter(Date.from(now.toInstant().plus(3650, ChronoUnit.DAYS)))
        generator.setPublicKey(keyPair.public)
        generator.setSignatureAlgorithm("SHA256withRSA")
        return generator.generate(keyPair.private)
    }

    private companion object {
        const val DEFAULT_ALIAS = "vibe-design-test"
        val DEFAULT_PASS = "vibetest".toCharArray()
    }
}
