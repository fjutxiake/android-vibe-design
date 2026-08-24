package com.aeibi.design.data.securestore

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeibi.design.ai.provider.DEEPSEEK_PROVIDER_TYPE
import com.aeibi.design.ai.provider.OPENAI_PROVIDER_TYPE
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.data.ai.AiProviderRepository
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecureStoreTest {
    @Test
    fun value_isEncryptedAndCanBeDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidKeystoreSecureStore(context)
        val key = "test.secret.${UUID.randomUUID()}"
        val value = "test-secret-value"
        val encryptedFile = secureStoreFile(context.filesDir, key)

        try {
            store.put(key, value)

            assertTrue(store.contains(key))
            assertEquals(value, store.get(key))
            assertFalse(encryptedFile.readBytes().toString(Charsets.UTF_8).contains(value))

            store.delete(key)
            assertFalse(store.contains(key))
            assertNull(store.get(key))
        } finally {
            store.delete(key)
        }
    }

    @Test
    fun providerMetadataAndApiKey_arePersistedSeparately() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secureStore = AndroidKeystoreSecureStore(context)
        val repository = AiProviderRepository(context, secureStore)
        val config = ProviderConfig(
            id = UUID.randomUUID().toString(),
            providerType = OPENAI_PROVIDER_TYPE,
            displayName = "Test Provider",
            endpoint = "https://example.com/v1",
            models = listOf("test-model", "test-model-2")
        )
        val apiKey = "separate-test-secret"

        try {
            repository.saveProvider(config, apiKey)

            val persisted = repository.settings.first { settings ->
                settings.providers.any { it.id == config.id }
            }
            assertEquals(config, persisted.providers.first { it.id == config.id })
            assertEquals(apiKey, repository.readApiKey(config.id))

            val settingsFile = File(context.filesDir, "datastore/ai_settings.preferences_pb")
            assertFalse(settingsFile.readBytes().toString(Charsets.UTF_8).contains(apiKey))
        } finally {
            repository.deleteProvider(config.id)
        }
    }

    @Test
    fun providerCanPersistAnEmptyApiKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secureStore = AndroidKeystoreSecureStore(context)
        val repository = AiProviderRepository(context, secureStore)
        val config = testProviderConfig("Local Provider")

        try {
            repository.saveProvider(config, "")

            assertTrue(secureStore.contains(config.id))
            assertEquals("", repository.readApiKey(config.id))
        } finally {
            repository.deleteProvider(config.id)
        }
    }

    @Test
    fun multipleRoutesOfTheSameProviderType_arePersisted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secureStore = AndroidKeystoreSecureStore(context)
        val repository = AiProviderRepository(context, secureStore)
        val first = testProviderConfig("DeepSeek Main")
        val second = testProviderConfig("DeepSeek Backup")

        try {
            repository.saveProvider(first, "first-secret")
            repository.saveProvider(second, "second-secret")

            val persisted = repository.settings.first { settings ->
                settings.providers.any { it.id == first.id } &&
                    settings.providers.any { it.id == second.id }
            }
            assertEquals(
                setOf(first, second),
                persisted.providers.filter { it.id == first.id || it.id == second.id }.toSet()
            )
        } finally {
            repository.deleteProvider(first.id)
            repository.deleteProvider(second.id)
        }
    }

    private fun testProviderConfig(displayName: String) = ProviderConfig(
        id = UUID.randomUUID().toString(),
        providerType = DEEPSEEK_PROVIDER_TYPE,
        displayName = displayName,
        endpoint = "https://api.deepseek.com",
        models = listOf("deepseek-v4-flash", "custom-model")
    )

    private fun secureStoreFile(filesDir: File, key: String): File {
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        val filename = Base64.encodeToString(
            keyHash,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return File(filesDir, "secure_store/$filename.bin")
    }
}
