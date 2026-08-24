package com.aeibi.design.data.securestore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidKeystoreSecureStore @Inject constructor(@ApplicationContext context: Context) : SecureStore {
    private val secureStoreDirectory = File(context.filesDir, SECURE_STORE_DIRECTORY)

    override fun contains(key: String): Boolean = valueFile(key).isFile

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val file = valueFile(key)
        secureStoreDirectory.mkdirs()

        val plainText = value.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(plainText)
            val envelope = ByteBuffer.allocate(2 + cipher.iv.size + encrypted.size)
                .put(FORMAT_VERSION)
                .put(cipher.iv.size.toByte())
                .put(cipher.iv)
                .put(encrypted)
                .array()

            val atomicFile = AtomicFile(file)
            val output = atomicFile.startWrite()
            try {
                output.write(envelope)
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        } finally {
            plainText.fill(0)
        }
        Unit
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val file = valueFile(key)
        if (!file.isFile) return@withContext null
        decrypt(file, key, getOrCreateKey())
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        AtomicFile(valueFile(key)).delete()
        Unit
    }

    private fun decrypt(file: File, key: String, secretKey: SecretKey): String {
        val envelope = AtomicFile(file).openRead().use { it.readBytes() }
        require(envelope.size > 2 && envelope[0] == FORMAT_VERSION) {
            "Unsupported secure store format."
        }

        val ivSize = envelope[1].toInt() and 0xff
        require(ivSize in 12..16 && envelope.size > 2 + ivSize) {
            "Invalid secure store IV."
        }

        val iv = envelope.copyOfRange(2, 2 + ivSize)
        val encrypted = envelope.copyOfRange(2 + ivSize, envelope.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val plainText = cipher.doFinal(encrypted)
        return try {
            plainText.toString(Charsets.UTF_8)
        } finally {
            plainText.fill(0)
        }
    }

    private fun valueFile(key: String): File {
        require(key.isNotBlank()) { "Secure store key must not be blank." }
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        val filename = Base64.encodeToString(
            keyHash,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return File(secureStoreDirectory, "$filename.bin")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val SECURE_STORE_DIRECTORY = "secure_store"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vibe_design_secure_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
    }
}
