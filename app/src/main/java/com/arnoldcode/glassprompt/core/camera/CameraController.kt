package com.arnoldcode.glassprompt.core.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import kotlinx.coroutines.flow.StateFlow

/** What the bound camera can actually do; the UI only offers these options. */
data class CameraCapabilities(
    val availableLenses: Set<CameraLens>,
    val resolutions: Set<VideoResolution>,
    val frameRates: Set<Int>,
    val hasTorch: Boolean,
    val exposureRange: IntRange,
    /** EV per exposure index step (e.g. 1/3). */
    val exposureStep: Float,
    val minZoom: Float,
    val maxZoom: Float,
)

/** The configuration that was really applied after falling back to supported values. */
data class BoundCamera(
    val lens: CameraLens,
    val resolution: VideoResolution,
    val frameRate: Int,
    val capabilities: CameraCapabilities,
)

sealed interface RecorderEvent {
    data class Progress(val elapsedMs: Long) : RecorderEvent
    data class Paused(val elapsedMs: Long) : RecorderEvent
    data class Resumed(val elapsedMs: Long) : RecorderEvent

    /** Recording closed. [error] is null when the file is valid. */
    data class Finished(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val error: AppError?,
    ) : RecorderEvent
}

/**
 * Camera preview + video recording. Implemented with CameraX in the data layer; the camera
 * screen's ViewModel only talks to this interface so it can be tested without hardware.
 */
interface CameraController {
    /** Surface to render the preview into; null until bound. */
    val surfaceRequest: StateFlow<SurfaceRequest?>
    val zoomRatio: StateFlow<Float>

    suspend fun bind(
        owner: LifecycleOwner,
        lens: CameraLens,
        resolution: VideoResolution,
        frameRate: Int,
    ): AppResult<BoundCamera>

    fun unbind()

    fun setZoomRatio(ratio: Float)

    /** Focus and meter at a point in preview-surface coordinates. */
    fun focusAt(surfaceX: Float, surfaceY: Float)

    fun setExposureIndex(index: Int)
    fun setTorch(enabled: Boolean)

    fun startRecording(filePath: String, onEvent: (RecorderEvent) -> Unit): AppResult<Unit>
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording()
}
