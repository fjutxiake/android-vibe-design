package com.aeibi.design.feature.settings.ai

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.R
import com.aeibi.design.ai.provider.AiProviderRegistry
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.ai.provider.ProviderDefinition
import com.aeibi.design.data.ai.AiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderConfigItem(val config: ProviderConfig, val selectedModelId: String? = null)

data class AiProvidersUiState(
    val configuredProviders: List<ProviderConfigItem> = emptyList(),
    val providerDefinitions: List<ProviderDefinition> = emptyList(),
    val isSaving: Boolean = false,
    @StringRes val feedback: Int? = null
)

private data class OperationState(val isSaving: Boolean = false, @StringRes val feedback: Int? = null)

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val repository: AiProviderRepository,
    private val providerRegistry: AiProviderRegistry
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())

    val uiState = combine(repository.settings, operation) { settings, operation ->
        AiProvidersUiState(
            configuredProviders = settings.providers.map { config ->
                ProviderConfigItem(
                    config = config,
                    selectedModelId = settings.selectedModelId.takeIf { settings.selectedProviderId == config.id }
                )
            },
            providerDefinitions = providerRegistry.definitions,
            isSaving = operation.isSaving,
            feedback = operation.feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiProvidersUiState(providerDefinitions = providerRegistry.definitions)
    )

    fun saveProvider(config: ProviderConfig, apiKey: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            operation.value = OperationState(isSaving = true)
            val error = runCatching { repository.saveProvider(config, apiKey) }
                .exceptionOrNull()
            operation.value = if (error == null) {
                OperationState(feedback = R.string.ai_config_saved)
            } else {
                OperationState(feedback = error.safeMessageRes())
            }
            onComplete(error == null)
        }
    }

    fun deleteProvider(configId: String) {
        viewModelScope.launch {
            val error = runCatching { repository.deleteProvider(configId) }.exceptionOrNull()
            operation.value = if (error == null) {
                OperationState(feedback = R.string.ai_config_removed)
            } else {
                OperationState(feedback = R.string.ai_delete_failed)
            }
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            val error = runCatching { repository.selectModel(providerId, modelId) }.exceptionOrNull()
            if (error != null) operation.value = OperationState(feedback = R.string.ai_selection_failed)
        }
    }

    suspend fun revealApiKey(configId: String): String? = runCatching {
        repository.readApiKey(configId)
    }.getOrElse {
        operation.value = OperationState(feedback = R.string.ai_read_key_failed)
        null
    }

    fun clearFeedback() {
        operation.value = operation.value.copy(feedback = null)
    }
}

@StringRes
private fun Throwable.safeMessageRes(): Int = when (message) {
    "Provider name is required" -> R.string.ai_err_name_empty
    "API endpoint is required" -> R.string.ai_err_endpoint_empty
    "At least one model is required" -> R.string.ai_err_models_empty
    else -> R.string.ai_save_failed
}
