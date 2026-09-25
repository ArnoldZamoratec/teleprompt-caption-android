package com.arnoldcode.glassprompt.feature.projectsetup

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.VideoOrientation
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.usecase.CreateProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetTemplatesUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateProjectUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SetupResult {
    data class Created(val projectId: String) : SetupResult
    data object Updated : SetupResult
}

@Immutable
data class ProjectSetupUiState(
    val isEditing: Boolean,
    val isLoading: Boolean = false,
    val name: String = "",
    val showNameError: Boolean = false,
    val templateTitle: String? = null,
    val recording: RecordingSettings = RecordingSettings(),
    val teleprompter: TeleprompterSettings = TeleprompterSettings(),
    val isSaving: Boolean = false,
    @param:StringRes val errorMessage: Int? = null,
    val result: SetupResult? = null,
)

/**
 * Create or edit a project. Arguments come from the type-safe route
 * [com.arnoldcode.glassprompt.core.navigation.Route.ProjectSetup] (read by key so the ViewModel stays JVM-testable).
 */
@HiltViewModel
class ProjectSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProject: GetProjectUseCase,
    private val createProject: CreateProjectUseCase,
    private val updateProject: UpdateProjectUseCase,
    private val getTemplates: GetTemplatesUseCase,
) : ViewModel() {

    private val projectId: String? = savedStateHandle[ARG_PROJECT_ID]
    private val templateId: String? = savedStateHandle[ARG_TEMPLATE_ID]

    private var original: Project? = null
    private var templateBody: String = ""

    private val _uiState = MutableStateFlow(ProjectSetupUiState(isEditing = projectId != null, isLoading = projectId != null))
    val uiState: StateFlow<ProjectSetupUiState> = _uiState.asStateFlow()

    init {
        if (projectId != null) loadProject(projectId) else templateId?.let(::applyTemplate)
    }

    private fun loadProject(id: String) = viewModelScope.launch {
        when (val result = getProject(id)) {
            is AppResult.Success -> {
                original = result.data
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = result.data.name,
                        recording = result.data.recording,
                        teleprompter = result.data.teleprompter,
                    )
                }
            }
            is AppResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.error.messageRes()) }
        }
    }

    private fun applyTemplate(id: String) {
        val template = (getTemplates.byId(id) as? AppResult.Success)?.data ?: return
        templateBody = template.body
        _uiState.update { it.copy(name = template.title, templateTitle = template.title) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(Project.MAX_NAME_LENGTH), showNameError = false) }
    }

    fun onLensSelected(lens: CameraLens) = updateRecording { copy(lens = lens) }
    fun onOrientationSelected(orientation: VideoOrientation) = updateRecording { copy(orientation = orientation) }
    fun onResolutionSelected(resolution: VideoResolution) = updateRecording { copy(resolution = resolution) }
    fun onFrameRateSelected(fps: Int) = updateRecording { copy(frameRate = fps) }

    fun onSpeedChange(speed: Float) = updateTeleprompter {
        copy(speed = speed.coerceIn(TeleprompterSettings.SpeedRange))
    }

    fun onFontSizeChange(size: Float) = updateTeleprompter {
        copy(fontSizeSp = size.coerceIn(TeleprompterSettings.FontSizeRange))
    }

    fun onMirrorChange(mirror: Boolean) = updateTeleprompter { copy(mirror = mirror) }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(showNameError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val existing = original
            val result: AppResult<SetupResult> = if (existing == null) {
                when (val created = createProject(NewProject(state.name, state.recording, state.teleprompter, templateBody))) {
                    is AppResult.Success -> AppResult.Success(SetupResult.Created(created.data))
                    is AppResult.Failure -> created
                }
            } else {
                val updated = existing.copy(name = state.name, recording = state.recording, teleprompter = state.teleprompter)
                when (val saved = updateProject(updated)) {
                    is AppResult.Success -> AppResult.Success(SetupResult.Updated)
                    is AppResult.Failure -> saved
                }
            }
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, result = result.data)
                    is AppResult.Failure -> it.copy(
                        isSaving = false,
                        showNameError = result.error is AppError.InvalidInput,
                        errorMessage = result.error.takeUnless { e -> e is AppError.InvalidInput }?.messageRes(),
                    )
                }
            }
        }
    }

    fun onResultHandled() = _uiState.update { it.copy(result = null) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    private inline fun updateRecording(crossinline block: RecordingSettings.() -> RecordingSettings) =
        _uiState.update { it.copy(recording = it.recording.block()) }

    private inline fun updateTeleprompter(crossinline block: TeleprompterSettings.() -> TeleprompterSettings) =
        _uiState.update { it.copy(teleprompter = it.teleprompter.block()) }

    companion object {
        /** Keys match the property names of the type-safe route. */
        const val ARG_PROJECT_ID = "projectId"
        const val ARG_TEMPLATE_ID = "templateId"
    }
}
