package com.arnoldcode.glassprompt.feature.templates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.arnoldcode.glassprompt.domain.model.ScriptTemplate
import com.arnoldcode.glassprompt.domain.model.TemplateCategory
import com.arnoldcode.glassprompt.domain.usecase.GetTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class TemplatesUiState(
    val all: List<ScriptTemplate> = emptyList(),
    /** null = all categories. */
    val category: TemplateCategory? = null,
    val preview: ScriptTemplate? = null,
) {
    val categories: List<TemplateCategory> get() = all.map { it.category }.distinct()
    val visible: List<ScriptTemplate> get() = if (category == null) all else all.filter { it.category == category }
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    getTemplates: GetTemplatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState(all = getTemplates()))
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    fun onCategorySelected(category: TemplateCategory?) = _uiState.update { it.copy(category = category) }

    fun onPreview(template: ScriptTemplate) = _uiState.update { it.copy(preview = template) }

    fun onPreviewDismiss() = _uiState.update { it.copy(preview = null) }
}
