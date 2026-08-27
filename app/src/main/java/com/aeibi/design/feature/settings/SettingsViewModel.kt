package com.aeibi.design.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.ai.AiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(val aiProviderCount: Int = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(repository: AiProviderRepository) : ViewModel() {
    val uiState = repository.settings
        .map { settings -> SettingsUiState(aiProviderCount = settings.providers.size) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )
}
