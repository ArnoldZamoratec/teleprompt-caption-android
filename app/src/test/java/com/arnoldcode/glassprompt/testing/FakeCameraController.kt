package com.arnoldcode.glassprompt.testing

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.arnoldcode.glassprompt.core.camera.BoundCamera
import com.arnoldcode.glassprompt.core.camera.CameraCapabilities
import com.arnoldcode.glassprompt.core.camera.CameraController
import com.arnoldcode.glassprompt.core.camera.RecorderEvent
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scriptable camera: tests drive recorder callbacks with [emit]. */
class FakeCameraController(
    var capabilities: CameraCapabilities = CameraCapabilities(
        availableLenses = setOf(CameraLens.FRONT, CameraLens.BACK),
        resolutions = setOf(VideoResolution.HD_720, VideoResolution.FHD_1080),
        frameRates = setOf(24, 30),
        hasTorch = true,
        exposureRange = -6..6,
        exposureStep = 1 / 3f,
        minZoom = 1f,
        maxZoom = 8f,
    ),
) : CameraController {

    override val surfaceRequest: StateFlow<SurfaceRequest?> = MutableStateFlow(null)
    private val zoom = MutableStateFlow(1f)
    override val zoomRatio: StateFlow<Float> = zoom

    var bindError: AppError? = null
    var startError: AppError? = null
    val boundLenses = mutableListOf<CameraLens>()
    var torchOn = false
    var exposure = 0
    var recordingFile: String? = null
    var paused = false
    var stopped = false
    private var listener: ((RecorderEvent) -> Unit)? = null

    override suspend fun bind(owner: LifecycleOwner, lens: CameraLens, resolution: VideoResolution, frameRate: Int): AppResult<BoundCamera> {
        bindError?.let { return AppResult.Failure(it) }
        boundLenses += lens
        val actualResolution = if (resolution in capabilities.resolutions) resolution else capabilities.resolutions.maxBy { it.height }
        val actualFps = if (frameRate in capabilities.frameRates) frameRate else capabilities.frameRates.max()
        return AppResult.Success(BoundCamera(lens, actualResolution, actualFps, capabilities))
    }

    override fun unbind() = Unit
    override fun setZoomRatio(ratio: Float) {
        zoom.value = ratio.coerceIn(capabilities.minZoom, capabilities.maxZoom)
    }
    override fun focusAt(surfaceX: Float, surfaceY: Float) = Unit
    override fun setExposureIndex(index: Int) {
        exposure = index
    }
    override fun setTorch(enabled: Boolean) {
        torchOn = enabled
    }

    override fun startRecording(filePath: String, onEvent: (RecorderEvent) -> Unit): AppResult<Unit> {
        startError?.let { return AppResult.Failure(it) }
        recordingFile = filePath
        listener = onEvent
        return AppResult.Success(Unit)
    }

    override fun pauseRecording() {
        paused = true
    }

    override fun resumeRecording() {
        paused = false
    }

    override fun stopRecording() {
        stopped = true
    }

    fun emit(event: RecorderEvent) = listener?.invoke(event)
}

/** A LifecycleOwner is only passed through to the fake camera. */
object NoLifecycleOwner : LifecycleOwner {
    override val lifecycle get() = throw UnsupportedOperationException("not used by FakeCameraController")
}
