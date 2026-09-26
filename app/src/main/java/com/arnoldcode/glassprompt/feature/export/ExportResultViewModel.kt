package com.arnoldcode.glassprompt.feature.export

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.repository.SubtitleFiles
import com.arnoldcode.glassprompt.domain.usecase.DeleteExportUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveExportUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveToGalleryUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Immutable
data class ExportResultUiState(
    val isLoading: Boolean = true,
    val export: ExportedVideo? = null,
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    @param:StringRes val message: Int? = null,
)

sealed interface ExportResultEvent {
    data class ShareSubtitles(val path: String) : ExportResultEvent
}

@HiltViewModel
class ExportResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExport: ObserveExportUseCase,
    private val saveToGallery: SaveToGalleryUseCase,
    private val deleteExport: DeleteExportUseCase,
    private val subtitles: SubtitleFiles,
) : ViewModel() {

    private val exportId: String = checkNotNull(savedStateHandle[ARG_EXPORT_ID]) { "exportId is required" }

    private val _uiState = MutableStateFlow(ExportResultUiState())
    val uiState: StateFlow<ExportResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExportResultEvent>(Channel.BUFFERED)
    val events: Flow<ExportResultEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeExport(exportId).collect { export ->
                // A file removed behind our back (cleared storage) counts as gone.
                _uiState.update { it.copy(isLoading = false, export = export?.takeIf { e -> File(e.filePath).exists() }) }
            }
        }
    }

    fun onSaveToGallery() {
        val export = _uiState.value.export ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveToGallery(export)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = when (result) {
                        is AppResult.Success -> R.string.result_saved
                        is AppResult.Failure -> result.error.messageRes()
                    },
                )
            }
        }
    }

    fun onShareSubtitles() {
        val export = _uiState.value.export ?: return
        viewModelScope.launch {
            val baseName = export.projectName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "subtitulos" }
            when (val result = subtitles.writeSrt(export.takeId, baseName)) {
                is AppResult.Success -> _events.send(ExportResultEvent.ShareSubtitles(result.data))
                is AppResult.Failure -> _uiState.update { it.copy(message = R.string.result_no_subtitles) }
            }
        }
    }

    fun onDeleteRequest() = _uiState.update { it.copy(confirmDelete = true) }

    fun onDeleteDismiss() = _uiState.update { it.copy(confirmDelete = false) }

    fun onDeleteConfirm() {
        _uiState.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val result = deleteExport(exportId)) {
                is AppResult.Success -> _uiState.update { it.copy(deleted = true) }
                is AppResult.Failure -> _uiState.update { it.copy(message = result.error.messageRes()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        const val ARG_EXPORT_ID = "exportId"
    }
}
