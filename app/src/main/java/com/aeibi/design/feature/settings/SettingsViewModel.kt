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

data class SettingsUiState(val aiProviderCount: Int = 0, val language: LanguagePreference = LanguagePreference.SYSTEM)

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

    /** 写入语言偏好；UI 经响应式状态与 LocalContext 覆盖即时更新，无需重建 Activity。 */
    fun setLanguage(preference: LanguagePreference) {
        languagePreferenceStore.write(preference)
    }
}
