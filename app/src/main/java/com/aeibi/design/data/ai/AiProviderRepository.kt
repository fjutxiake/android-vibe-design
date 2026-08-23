package com.aeibi.design.data.ai

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.data.securestore.SecureStore
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ai_settings")

@Singleton
class AiProviderRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStore: SecureStore
) {
    val settings: Flow<AiProviderSettings> = context.aiSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decodeSettings)

    suspend fun saveProvider(config: ProviderConfig, apiKey: String) {
        val normalized = config.copy(
            displayName = config.displayName.trim(),
            endpoint = config.endpoint.trim().trimEnd('/'),
            models = config.models.map(String::trim).filter(String::isNotEmpty).distinct()
        )

        require(normalized.displayName.isNotEmpty()) { "配置名称不能为空" }
        require(normalized.endpoint.isNotEmpty()) { "API 地址不能为空" }
        require(normalized.models.isNotEmpty()) { "至少添加一个模型" }

        secureStore.put(normalized.id, apiKey)

        context.aiSettingsDataStore.edit { preferences ->
            val current = decodeSettings(preferences)
            val providers = current.providers
                .filterNot { it.id == normalized.id }
                .plus(normalized)
            preferences[SETTINGS_KEY] = encodeSettings(AiProviderSettings(providers))
        }
    }

    suspend fun deleteProvider(configId: String) {
        context.aiSettingsDataStore.edit { preferences ->
            val current = decodeSettings(preferences)
            preferences[SETTINGS_KEY] = encodeSettings(
                AiProviderSettings(current.providers.filterNot { it.id == configId })
            )
        }
        secureStore.delete(configId)
    }

    fun hasApiKey(configId: String): Boolean = secureStore.contains(configId)

    suspend fun readApiKey(configId: String): String? = secureStore.get(configId)

    private fun decodeSettings(preferences: Preferences): AiProviderSettings = preferences[SETTINGS_KEY]
        ?.let(::decodeSettings)
        ?: AiProviderSettings()

    private fun encodeSettings(settings: AiProviderSettings): String = JSONObject()
        .put("version", SETTINGS_VERSION)
        .put(
            "providers",
            JSONArray().apply {
                settings.providers.forEach { config ->
                    put(
                        JSONObject()
                            .put("id", config.id)
                            .put("providerType", config.providerType)
                            .put("displayName", config.displayName)
                            .put("endpoint", config.endpoint)
                            .put("models", JSONArray(config.models))
                    )
                }
            }
        )
        .toString()

    private fun decodeSettings(encoded: String): AiProviderSettings = runCatching {
        val root = JSONObject(encoded)
        val providersJson = root.optJSONArray("providers")
            ?: root.optJSONArray("profiles")
            ?: JSONArray()
        val providers = buildList {
            for (index in 0 until providersJson.length()) {
                val config = providersJson.optJSONObject(index) ?: continue
                val id = config.optString("id")
                val providerType = config.optString("providerType")
                    .ifBlank { config.optString("providerId") }
                if (id.isBlank() || providerType.isBlank() || runCatching { UUID.fromString(id) }.isFailure) continue
                val models = config.optJSONArray("models")?.let { modelsJson ->
                    buildList {
                        for (modelIndex in 0 until modelsJson.length()) {
                            modelsJson.optString(modelIndex).trim().takeIf(String::isNotEmpty)?.let(::add)
                        }
                    }
                } ?: listOfNotNull(config.optString("model").trim().takeIf(String::isNotEmpty))
                add(
                    ProviderConfig(
                        id = id,
                        providerType = providerType,
                        displayName = config.optString("displayName"),
                        endpoint = config.optString("endpoint"),
                        models = models.distinct()
                    )
                )
            }
        }
        AiProviderSettings(providers)
    }.getOrDefault(AiProviderSettings())

    private companion object {
        const val SETTINGS_VERSION = 3
        val SETTINGS_KEY = stringPreferencesKey("profiles")
    }
}
