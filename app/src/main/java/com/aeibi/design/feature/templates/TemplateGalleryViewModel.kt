package com.aeibi.design.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.templates.Template
import com.aeibi.design.data.templates.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateGalleryUiState(
    val templates: List<Template> = emptyList(),
    val selectedTemplate: Template? = null,
    val readme: String? = null,
    val isLoading: Boolean = true,
    val isReadmeLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isApplyingTemplate: Boolean = false,
    val applyFailed: Boolean = false
)

@HiltViewModel
class TemplateGalleryViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateGalleryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            val result = runCatching { templateRepository.listTemplates() }
            _uiState.update {
                it.copy(
                    templates = result.getOrDefault(emptyList()),
                    isLoading = false,
                    loadFailed = result.isFailure
                )
            }
        }
    }

    fun openTemplate(template: Template) {
        _uiState.update {
            it.copy(
                selectedTemplate = template,
                readme = null,
                isReadmeLoading = template.readmeAssetPath != null,
                applyFailed = false
            )
        }
        if (template.readmeAssetPath == null) return

        viewModelScope.launch {
            val readme = runCatching { templateRepository.readReadme(template) }.getOrNull()
            _uiState.update { current ->
                if (current.selectedTemplate?.id == template.id) {
                    current.copy(readme = readme, isReadmeLoading = false)
                } else {
                    current
                }
            }
        }
    }

    fun closeTemplate() {
        if (_uiState.value.isApplyingTemplate) return
        _uiState.update {
            it.copy(
                selectedTemplate = null,
                readme = null,
                isReadmeLoading = false,
                applyFailed = false
            )
        }
    }

    fun initializeProject(projectId: String, onSuccess: () -> Unit) {
        val template = _uiState.value.selectedTemplate ?: return
        if (_uiState.value.isApplyingTemplate) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingTemplate = true, applyFailed = false) }
            val result = runCatching {
                projectRepository.initializeFromTemplate(projectId, template.workspaceAssetPath)
            }
            _uiState.update {
                it.copy(
                    isApplyingTemplate = false,
                    applyFailed = result.isFailure
                )
            }
            if (result.isSuccess) onSuccess()
        }
    }
}
