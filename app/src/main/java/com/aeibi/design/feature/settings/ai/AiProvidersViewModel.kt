package com.aeibi.design.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class ProviderConfigItem(val config: ProviderConfig, val hasApiKey: Boolean)

data class AiProvidersUiState(
    val configuredProviders: List<ProviderConfigItem> = emptyList(),
    val providerDefinitions: List<ProviderDefinition> = emptyList(),
    val isSaving: Boolean = false,
    val feedback: String? = null
)

private data class OperationState(val isSaving: Boolean = false, val feedback: String? = null)

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val repository: AiProviderRepository,
    private val providerRegistry: AiProviderRegistry
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())

    val uiState = combine(repository.settings, operation) { settings, operation ->
        AiProvidersUiState(
            configuredProviders = settings.providers.map { config ->
                ProviderConfigItem(config, repository.hasApiKey(config.id))
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
                OperationState(feedback = "配置已保存")
            } else {
                OperationState(feedback = error.safeMessage("保存失败"))
            }
            onComplete(error == null)
        }
    }

    fun deleteProvider(configId: String) {
        viewModelScope.launch {
            val error = runCatching { repository.deleteProvider(configId) }.exceptionOrNull()
            operation.value = if (error == null) {
                OperationState(feedback = "配置已移除")
            } else {
                OperationState(feedback = "移除配置失败")
            }
        }
    }

    suspend fun revealApiKey(configId: String): String? = runCatching {
        repository.readApiKey(configId)
    }.getOrElse {
        operation.value = OperationState(feedback = "无法读取已保存的 API Key")
        null
    }

    fun clearFeedback() {
        operation.value = operation.value.copy(feedback = null)
    }
}

private fun Throwable.safeMessage(fallback: String): String = message
    ?.takeIf { it in SAFE_MESSAGES }
    ?: fallback

private val SAFE_MESSAGES = setOf(
    "配置名称不能为空",
    "API 地址不能为空",
    "至少添加一个模型"
)
