package com.arnoldcode.glassprompt.feature.export

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.ExportOptions
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.usecase.ExportControlUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveTakeUseCase
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
data class ExportUiState(
    val isLoading: Boolean = true,
    val take: Take? = null,
    val resolutions: List<VideoResolution> = emptyList(),
    val frameRates: List<Int> = emptyList(),
    val hasCaptions: Boolean = false,
    val settings: ExportSettings? = null,
    val outputSize: Pair<Int, Int>? = null,
    val estimatedBytes: Long = 0,
    val export: ExportState = ExportState.Idle,
    /** Set once when the export finishes; the screen navigates to the result and consumes it. */
    val finishedExportId: String? = null,
) {
    val isExporting: Boolean get() = export is ExportState.Running
}

/**
 * Export options for one take (only what the take can honestly deliver) and the progress of
 * its background export. Leaving the screen does not stop the export.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTake: ObserveTakeUseCase,
    private val observeTrack: ObserveCaptionTrackUseCase,
    private val control: ExportControlUseCase,
) : ViewModel() {

    private val takeId: String = checkNotNull(savedStateHandle[ARG_TAKE_ID]) { "takeId is required" }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val take = observeTake(takeId).first()?.takeIf { File(it.filePath).exists() }
            if (take == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val hasCaptions = observeTrack(takeId).first()?.captions?.isNotEmpty() == true
            _uiState.update {
                it.copy(
                    isLoading = false,
                    take = take,
                    resolutions = ExportOptions.resolutions(take),
                    frameRates = ExportOptions.frameRates(take),
                    hasCaptions = hasCaptions,
                ).withSettings(ExportOptions.defaults(take, hasCaptions))
            }
        }
        viewModelScope.launch {
            // A result left over from an earlier visit (Done/Failed before anything ran here) is stale.
            var sawRunning = false
            control.observe(takeId).collect { state ->
                if (state is ExportState.Running) sawRunning = true
                val current = if (sawRunning || state is ExportState.Running) state else ExportState.Idle
                _uiState.update {
                    it.copy(export = current, finishedExportId = (current as? ExportState.Done)?.exportId ?: it.finishedExportId)
                }
            }
        }
    }

    fun onResolution(resolution: VideoResolution) = updateSettings { it.copy(resolution = resolution) }

    fun onFrameRate(frameRate: Int) = updateSettings { it.copy(frameRate = frameRate) }

    fun onCaptions(enabled: Boolean) = updateSettings { it.copy(burnCaptions = enabled && _uiState.value.hasCaptions) }

    fun onStart() {
        val settings = _uiState.value.settings ?: return
        if (_uiState.value.isExporting) return
        control.start(takeId, settings)
    }

    fun onCancel() = control.cancel(takeId)

    /** The screen navigated to the result: forget the finished export so coming back starts clean. */
    fun onFinishedShown() {
        control.clear(takeId)
        _uiState.update { it.copy(finishedExportId = null, export = ExportState.Idle) }
    }

    /** Dismisses a failure message. */
    fun onErrorShown() {
        control.clear(takeId)
        _uiState.update { it.copy(export = ExportState.Idle) }
    }

    private fun updateSettings(change: (ExportSettings) -> ExportSettings) {
        if (_uiState.value.isExporting) return
        _uiState.update { state -> state.settings?.let { state.withSettings(change(it)) } ?: state }
    }

    private fun ExportUiState.withSettings(settings: ExportSettings): ExportUiState {
        val take = take ?: return this
        val size = ExportOptions.outputSize(take.width, take.height, settings.resolution)
        return copy(
            settings = settings,
            outputSize = size,
            estimatedBytes = ExportOptions.estimateBytes(size.first, size.second, minOf(settings.frameRate, take.frameRate), take.durationMs),
        )
    }

    companion object {
        const val ARG_TAKE_ID = "takeId"
    }
}
