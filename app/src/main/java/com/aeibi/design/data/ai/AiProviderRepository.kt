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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ai_settings")

/** 全局默认的 provider/model:新会话创建时继承为出生值,此后全局变化不影响存量会话。 */
data class DefaultProviderSelection(val providerConfigId: String?, val model: String?) {
    val isSet: Boolean get() = providerConfigId != null && model != null
}

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

    /** 全局默认 provider/model;未设置时 [DefaultProviderSelection.isSet] 为 false。 */
    val defaultSelection: Flow<DefaultProviderSelection> = context.aiSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decodeDefaultSelection)

    /** 设置全局默认(同时清空 key 时置 null)。 */
    suspend fun setDefaultSelection(providerConfigId: String?, model: String?) {
        context.aiSettingsDataStore.edit { preferences ->
            val root = JSONObject(preferences[SETTINGS_KEY] ?: "{}")
            if (providerConfigId == null || model == null) {
                root.remove("defaultProviderConfigId")
                root.remove("defaultModel")
            } else {
                root.put("defaultProviderConfigId", providerConfigId)
                root.put("defaultModel", model)
            }
            preferences[SETTINGS_KEY] = root.toString()
        }
    }

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
            val root = JSONObject(preferences[SETTINGS_KEY] ?: "{}")
            val providers = decodeSettings(preferences).providers.filterNot { it.id == configId }
            root.put(
                "providers",
                JSONArray().apply {
                    providers.forEach { config ->
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
            // 被删配置若正是全局默认,一并清除,避免悬空引用。
            if (root.optString("defaultProviderConfigId") == configId) {
                root.remove("defaultProviderConfigId")
                root.remove("defaultModel")
            }
            preferences[SETTINGS_KEY] = root.toString()
        }
        secureStore.delete(configId)
    }

    /** 文件系统检查(stat),挂起执行避免调用方在主线程做磁盘 IO。 */
    suspend fun hasApiKey(configId: String): Boolean = withContext(Dispatchers.IO) {
        secureStore.contains(configId)
    }

    suspend fun readApiKey(configId: String): String? = secureStore.get(configId)

    private fun decodeDefaultSelection(preferences: Preferences): DefaultProviderSelection {
        val root = runCatching { JSONObject(preferences[SETTINGS_KEY] ?: return DefaultProviderSelection(null, null)) }
            .getOrDefault(JSONObject())
        return DefaultProviderSelection(
            providerConfigId = root.optString("defaultProviderConfigId").takeIf(String::isNotEmpty),
            model = root.optString("defaultModel").takeIf(String::isNotEmpty)
        )
    }

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
        const val SETTINGS_VERSION = 4
        val SETTINGS_KEY = stringPreferencesKey("profiles")
    }
}
