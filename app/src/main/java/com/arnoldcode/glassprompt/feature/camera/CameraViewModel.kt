package com.arnoldcode.glassprompt.feature.camera

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.camera.BoundCamera
import com.arnoldcode.glassprompt.core.camera.CameraController
import com.arnoldcode.glassprompt.core.camera.RecorderEvent
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.ApplicationScope
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.RecordingState
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.VideoOrientation
import com.arnoldcode.glassprompt.domain.usecase.PrepareRecordingUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveTakeUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import com.arnoldcode.glassprompt.feature.teleprompter.PrompterAdjust
import com.arnoldcode.glassprompt.feature.teleprompter.PrompterSettingsSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CameraUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val projectName: String = "",
    val script: String = "",
    val wordCount: Int = 0,
    val teleprompter: TeleprompterSettings = TeleprompterSettings(),
    val requested: RecordingSettings = RecordingSettings(),
    /** Lens to bind next; the UI rebinds when it changes. */
    val lens: CameraLens = CameraLens.FRONT,
    val bound: BoundCamera? = null,
    val cameraError: Boolean = false,
    /** Bumped to ask the UI to bind again after an error. */
    val bindAttempt: Int = 0,
    val recording: RecordingState = RecordingState.Idle,
    val torchOn: Boolean = false,
    val exposureIndex: Int = 0,
    val confirmDiscard: Boolean = false,
    @param:StringRes val message: Int? = null,
    /** Set when a take was saved: navigate to its review. */
    val savedTakeId: String? = null,
    /** Set when the screen should close (back or discarded recording). */
    val exit: Boolean = false,
) {
    val isLandscape: Boolean get() = requested.orientation == VideoOrientation.LANDSCAPE
    val isBusy: Boolean get() = recording !is RecordingState.Idle
}

/** Commands for the on-screen prompter, which owns its scroll position. */
enum class PrompterCommand { Play, Pause }

/**
 * Camera + prompter. Record starts a countdown, then recording and the prompter together;
 * pause/resume drive both; stop saves the take and opens its review. Leaving mid-take asks
 * for confirmation and discards the file.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val camera: CameraController,
    private val session: PrompterSettingsSession,
    private val prepareRecording: PrepareRecordingUseCase,
    private val saveTake: SaveTakeUseCase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle[ARG_PROJECT_ID]) { "projectId is required" }

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val surfaceRequest = camera.surfaceRequest
    val zoomRatio = camera.zoomRatio

    private val _prompterCommands = Channel<PrompterCommand>(Channel.BUFFERED)
    val prompterCommands: Flow<PrompterCommand> = _prompterCommands.receiveAsFlow()

    private var countdownJob: Job? = null
    private var currentFile: String? = null
    private var discardRequested = false

    init {
        session.start(viewModelScope)
        viewModelScope.launch {
            val content = session.load(projectId)
            _uiState.update {
                if (content == null) {
                    it.copy(isLoading = false, notFound = true)
                } else {
                    it.copy(
                        isLoading = false,
                        projectName = content.project.name,
                        script = content.text,
                        wordCount = content.wordCount,
                        teleprompter = content.project.teleprompter,
                        requested = content.project.recording,
                        lens = content.project.recording.lens,
                    )
                }
            }
        }
    }

    // region Camera

    /** Binds the camera to [owner] with the current lens; called by the UI when ready. */
    suspend fun bindCamera(owner: LifecycleOwner) {
        val state = _uiState.value
        _uiState.update { it.copy(cameraError = false) }
        when (val result = camera.bind(owner, state.lens, state.requested.resolution, state.requested.frameRate)) {
            is AppResult.Success -> _uiState.update {
                it.copy(bound = result.data, lens = result.data.lens, torchOn = false, exposureIndex = 0)
            }
            is AppResult.Failure -> _uiState.update { it.copy(bound = null, cameraError = true) }
        }
    }

    fun onRetryCamera() = _uiState.update { it.copy(cameraError = false, bindAttempt = it.bindAttempt + 1) }

    fun onSwitchLens() {
        val state = _uiState.value
        val lenses = state.bound?.capabilities?.availableLenses ?: return
        if (state.isBusy || lenses.size < 2) return
        _uiState.update { it.copy(lens = if (it.lens == CameraLens.FRONT) CameraLens.BACK else CameraLens.FRONT) }
    }

    fun onTorchToggle() {
        val state = _uiState.value
        if (state.bound?.capabilities?.hasTorch != true) return
        camera.setTorch(!state.torchOn)
        _uiState.update { it.copy(torchOn = !it.torchOn) }
    }

    fun onZoomBy(factor: Float) = camera.setZoomRatio(zoomRatio.value * factor)

    fun onZoomTo(ratio: Float) = camera.setZoomRatio(ratio)

    fun onFocus(surfaceX: Float, surfaceY: Float) = camera.focusAt(surfaceX, surfaceY)

    fun onExposureChange(index: Int) {
        val range = _uiState.value.bound?.capabilities?.exposureRange ?: return
        val clamped = index.coerceIn(range)
        camera.setExposureIndex(clamped)
        _uiState.update { it.copy(exposureIndex = clamped) }
    }

    // endregion

    // region Recording

    /** Main button: start (after countdown) → stop. During the countdown it cancels. */
    fun onRecordClick() {
        when (_uiState.value.recording) {
            RecordingState.Idle -> startCountdown()
            is RecordingState.Countdown -> cancelCountdown()
            is RecordingState.Recording, is RecordingState.Paused -> stop()
            RecordingState.Finalizing -> Unit
        }
    }

    fun onPauseResumeClick() {
        when (_uiState.value.recording) {
            is RecordingState.Recording -> {
                camera.pauseRecording()
                _prompterCommands.trySend(PrompterCommand.Pause)
            }
            is RecordingState.Paused -> {
                camera.resumeRecording()
                _prompterCommands.trySend(PrompterCommand.Play)
            }
            else -> Unit
        }
    }

    private fun startCountdown() {
        if (_uiState.value.bound == null) return
        val seconds = _uiState.value.teleprompter.countdownSeconds
        countdownJob = viewModelScope.launch {
            for (n in seconds downTo 1) {
                _uiState.update { it.copy(recording = RecordingState.Countdown(n)) }
                delay(1_000)
            }
            startRecording()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(recording = RecordingState.Idle) }
    }

    private fun startRecording() {
        val file = when (val prepared = prepareRecording()) {
            is AppResult.Success -> prepared.data
            is AppResult.Failure -> {
                _uiState.update { it.copy(recording = RecordingState.Idle, message = prepared.error.messageRes()) }
                return
            }
        }
        currentFile = file
        discardRequested = false
        when (val started = camera.startRecording(file, ::onRecorderEvent)) {
            is AppResult.Success -> {
                _uiState.update { it.copy(recording = RecordingState.Recording(0)) }
                _prompterCommands.trySend(PrompterCommand.Play)
            }
            is AppResult.Failure -> {
                saveTake.discard(file)
                currentFile = null
                _uiState.update { it.copy(recording = RecordingState.Idle, message = started.error.messageRes()) }
            }
        }
    }

    private fun stop() {
        _uiState.update { it.copy(recording = RecordingState.Finalizing) }
        _prompterCommands.trySend(PrompterCommand.Pause)
        camera.stopRecording()
    }

    /** Recorder callbacks arrive on the main thread. */
    internal fun onRecorderEvent(event: RecorderEvent) {
        when (event) {
            is RecorderEvent.Progress -> _uiState.update {
                if (it.recording is RecordingState.Recording) it.copy(recording = RecordingState.Recording(event.elapsedMs)) else it
            }
            is RecorderEvent.Paused -> _uiState.update { it.copy(recording = RecordingState.Paused(event.elapsedMs)) }
            is RecorderEvent.Resumed -> _uiState.update { it.copy(recording = RecordingState.Recording(event.elapsedMs)) }
            is RecorderEvent.Finished -> onFinished(event)
        }
    }

    private fun onFinished(event: RecorderEvent.Finished) {
        val file = currentFile ?: return
        currentFile = null
        _prompterCommands.trySend(PrompterCommand.Pause)
        if (discardRequested || event.error != null) {
            saveTake.discard(file)
            _uiState.update {
                it.copy(
                    recording = RecordingState.Idle,
                    exit = discardRequested,
                    message = if (discardRequested) null else event.error?.messageRes(),
                )
            }
            return
        }
        val bound = _uiState.value.bound
        viewModelScope.launch {
            val result = saveTake(
                NewTake(
                    projectId = projectId,
                    filePath = file,
                    durationMs = event.durationMs,
                    width = event.width,
                    height = event.height,
                    frameRate = bound?.frameRate ?: _uiState.value.requested.frameRate,
                ),
            )
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(recording = RecordingState.Idle, savedTakeId = result.data)
                    is AppResult.Failure -> it.copy(recording = RecordingState.Idle, message = result.error.messageRes())
                }
            }
        }
    }

    /** Back: leave directly when idle, otherwise ask before discarding the take. */
    fun onBackRequest() {
        when (_uiState.value.recording) {
            RecordingState.Idle -> _uiState.update { it.copy(exit = true) }
            is RecordingState.Countdown -> cancelCountdown()
            else -> _uiState.update { it.copy(confirmDiscard = true) }
        }
    }

    fun onDiscardConfirm() {
        discardRequested = true
        _uiState.update { it.copy(confirmDiscard = false) }
        stop()
    }

    fun onDiscardDismiss() = _uiState.update { it.copy(confirmDiscard = false) }

    fun onSavedTakeHandled() = _uiState.update { it.copy(savedTakeId = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    // endregion

    // region Prompter settings

    fun onSpeedStep(up: Boolean) = editPrompter { PrompterAdjust.speed(it, up) }
    fun onFontSizeChange(size: Float) = editPrompter { PrompterAdjust.fontSize(it, size) }
    fun onFontStep(up: Boolean) = editPrompter { PrompterAdjust.fontStep(it, up) }

    private inline fun editPrompter(transform: (TeleprompterSettings) -> TeleprompterSettings) {
        val updated = transform(_uiState.value.teleprompter)
        _uiState.update { it.copy(teleprompter = updated) }
        session.onChanged(updated)
    }

    // endregion

    override fun onCleared() {
        countdownJob?.cancel()
        currentFile?.let {
            // Screen destroyed mid-take (e.g. process shutting down): don't leave half files.
            discardRequested = true
            camera.stopRecording()
        }
        appScope.launch { session.flush() }
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
