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

        require(normalized.displayName.isNotEmpty()) { "Provider name is required" }
        require(normalized.endpoint.isNotEmpty()) { "API endpoint is required" }
        require(normalized.models.isNotEmpty()) { "At least one model is required" }

        secureStore.put(normalized.id, apiKey)

        context.aiSettingsDataStore.edit { preferences ->
            val current = decodeSettings(preferences)
            val providers = current.providers
                .filterNot { it.id == normalized.id }
                .plus(normalized)
            val selectedProviderId = current.selectedProviderId ?: normalized.id
            val selectedModelId = if (selectedProviderId == normalized.id) {
                current.selectedModelId?.takeIf(normalized.models::contains) ?: normalized.models.first()
            } else {
                current.selectedModelId
            }
            preferences[SETTINGS_KEY] = encodeSettings(
                AiProviderSettings(providers, selectedProviderId, selectedModelId)
            )
        }
    }

    suspend fun selectModel(providerId: String, modelId: String) {
        context.aiSettingsDataStore.edit { preferences ->
            val current = decodeSettings(preferences)
            val provider = current.providers.firstOrNull { it.id == providerId }
                ?: error("Selected provider does not exist")
            require(modelId in provider.models) { "Selected model does not belong to the provider" }
            preferences[SETTINGS_KEY] = encodeSettings(
                current.copy(selectedProviderId = providerId, selectedModelId = modelId)
            )
        }
    }

    suspend fun deleteProvider(configId: String) {
        context.aiSettingsDataStore.edit { preferences ->
            val current = decodeSettings(preferences)
            val deletingSelection = current.selectedProviderId == configId
            preferences[SETTINGS_KEY] = encodeSettings(
                current.copy(
                    providers = current.providers.filterNot { it.id == configId },
                    selectedProviderId = current.selectedProviderId.takeUnless { deletingSelection },
                    selectedModelId = current.selectedModelId.takeUnless { deletingSelection }
                )
            )
        }
        secureStore.delete(configId)
    }

    suspend fun readApiKey(configId: String): String? = secureStore.get(configId)

    private fun decodeSettings(preferences: Preferences): AiProviderSettings = preferences[SETTINGS_KEY]
        ?.let(::decodeSettings)
        ?: AiProviderSettings()

    private fun encodeSettings(settings: AiProviderSettings): String = JSONObject()
        .put("version", SETTINGS_VERSION)
        .put("selectedProviderId", settings.selectedProviderId)
        .put("selectedModelId", settings.selectedModelId)
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
        require(root.optInt("version") == SETTINGS_VERSION)
        val providersJson = root.getJSONArray("providers")
        val providers = buildList {
            for (index in 0 until providersJson.length()) {
                val config = providersJson.getJSONObject(index)
                val modelsJson = config.getJSONArray("models")
                val models = buildList {
                    for (modelIndex in 0 until modelsJson.length()) {
                        modelsJson.getString(modelIndex).trim().takeIf(String::isNotEmpty)?.let(::add)
                    }
                }.distinct()
                add(
                    ProviderConfig(
                        id = config.getString("id"),
                        providerType = config.getString("providerType"),
                        displayName = config.getString("displayName"),
                        endpoint = config.getString("endpoint"),
                        models = models
                    )
                )
            }
        }
        val selectedProviderId = root.optString("selectedProviderId").takeIf(String::isNotBlank)
        val selectedModelId = root.optString("selectedModelId").takeIf(String::isNotBlank)
        val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
        require(selectedProviderId == null || selectedProvider != null)
        require(selectedModelId == null || selectedProvider?.models?.contains(selectedModelId) == true)
        AiProviderSettings(providers, selectedProviderId, selectedModelId)
    }.getOrDefault(AiProviderSettings())

    private companion object {
        const val SETTINGS_VERSION = 1
        val SETTINGS_KEY = stringPreferencesKey("settings")
    }
}
