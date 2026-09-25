package com.arnoldcode.glassprompt.feature.projects

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.usecase.DeleteProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.DuplicateProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveProjectsUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProjectsUiState(
    val query: String = "",
    /** null while the first load is in flight. */
    val projects: List<ProjectSummary>? = null,
    val pendingDelete: ProjectSummary? = null,
    @param:StringRes val message: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    observeProjects: ObserveProjectsUseCase,
    private val duplicateProject: DuplicateProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val transient = MutableStateFlow(ProjectsUiState())

    val uiState: StateFlow<ProjectsUiState> = combine(
        query.flatMapLatest { observeProjects(it) },
        transient,
    ) { projects, local -> local.copy(projects = projects) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun onQueryChange(value: String) {
        query.value = value
        transient.update { it.copy(query = value) }
    }

    fun onDuplicate(id: String) {
        viewModelScope.launch {
            val result = duplicateProject(id)
            transient.update {
                it.copy(message = if (result is AppResult.Failure) result.error.messageRes() else R.string.projects_duplicated)
            }
        }
    }

    fun onDeleteRequest(project: ProjectSummary) = transient.update { it.copy(pendingDelete = project) }

    fun onDeleteDismiss() = transient.update { it.copy(pendingDelete = null) }

    fun onDeleteConfirm() {
        val target = transient.value.pendingDelete ?: return
        transient.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteProject(target.id)
            transient.update {
                it.copy(message = if (result is AppResult.Failure) result.error.messageRes() else R.string.projects_deleted)
            }
        }
    }

    fun onMessageShown() = transient.update { it.copy(message = null) }
}
