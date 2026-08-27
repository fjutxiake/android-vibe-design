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

data class ProviderConfigItem(val config: ProviderConfig, val hasApiKey: Boolean)

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

/**
 * 把 Repository 抛出的已知验证异常消息映射成展示用资源 ID;
 * 未识别的异常统一回退到通用失败提示。
 *
 * Repository 当前用 require 抛带中文消息的 IllegalArgumentException;
 * 理想方案是 Repository 改抛 typed exception,但那超出 i18n 迁移的范围,
 * 这里先做字符串到资源 ID 的映射过渡。
 */
@StringRes
private fun Throwable.safeMessageRes(): Int = when (message) {
    "配置名称不能为空" -> R.string.ai_err_name_empty
    "API 地址不能为空" -> R.string.ai_err_endpoint_empty
    "至少添加一个模型" -> R.string.ai_err_models_empty
    else -> R.string.ai_save_failed
}
