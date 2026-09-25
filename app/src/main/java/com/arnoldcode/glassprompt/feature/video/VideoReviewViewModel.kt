package com.arnoldcode.glassprompt.feature.video

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.usecase.DeleteTakeUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.TranscriptionControlUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveTakeUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Immutable
data class VideoReviewUiState(
    val isLoading: Boolean = true,
    val take: Take? = null,
    val projectName: String = "",
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    @param:StringRes val message: Int? = null,
)

/** Review of a freshly recorded take: watch it, then caption it, export it or discard it. */
@HiltViewModel
class VideoReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTake: ObserveTakeUseCase,
    private val getProject: GetProjectUseCase,
    private val deleteTake: DeleteTakeUseCase,
    private val observeTrack: ObserveCaptionTrackUseCase,
    private val transcription: TranscriptionControlUseCase,
) : ViewModel() {

    private val takeId: String = checkNotNull(savedStateHandle[ARG_TAKE_ID]) { "takeId is required" }

    private val _uiState = MutableStateFlow(VideoReviewUiState())
    val uiState: StateFlow<VideoReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // A take whose file vanished (cleared storage) is treated as missing.
            val take = observeTake(takeId).first()?.takeIf { File(it.filePath).exists() }
            val name = take?.let { (getProject(it.projectId) as? AppResult.Success)?.data?.name }.orEmpty()
            _uiState.update { it.copy(isLoading = false, take = take, projectName = name) }
            // Start captioning while the user watches the take, so the editor opens ready.
            if (take != null && observeTrack(takeId).first() == null) transcription.start(takeId)
        }
    }

    fun onDeleteRequest() = _uiState.update { it.copy(confirmDelete = true) }

    fun onDeleteDismiss() = _uiState.update { it.copy(confirmDelete = false) }

    fun onDeleteConfirm() {
        _uiState.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val result = deleteTake(takeId)) {
                is AppResult.Success -> _uiState.update { it.copy(deleted = true) }
                is AppResult.Failure -> _uiState.update { it.copy(message = result.error.messageRes()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        const val ARG_TAKE_ID = "takeId"
    }
}
