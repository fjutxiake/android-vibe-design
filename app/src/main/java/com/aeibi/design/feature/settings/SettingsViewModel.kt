package com.aeibi.design.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.ai.AiProviderRepository
import com.aeibi.design.data.i18n.LanguagePreference
import com.aeibi.design.data.i18n.LanguagePreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(val aiProviderCount: Int = 0, val language: LanguagePreference = LanguagePreference.SYSTEM) {
    val aiProvidersSummary: String
        get() = when (aiProviderCount) {
            0 -> "尚未配置"
            else -> "已配置 $aiProviderCount 个服务"
        }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: AiProviderRepository,
    private val languagePreferenceStore: LanguagePreferenceStore
) : ViewModel() {
    val uiState = combine(repository.settings, languagePreferenceStore.changes) { settings, language ->
        SettingsUiState(
            aiProviderCount = settings.providers.size,
            language = language
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    /** 写入语言偏好；界面重建由调用方触发（recreate）。 */
    fun setLanguage(preference: LanguagePreference) {
        languagePreferenceStore.write(preference)
    }
}
