package com.arnoldcode.glassprompt.data.multimedia.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.arnoldcode.glassprompt.core.camera.BoundCamera
import com.arnoldcode.glassprompt.core.camera.CameraCapabilities
import com.arnoldcode.glassprompt.core.camera.CameraController
import com.arnoldcode.glassprompt.core.camera.RecorderEvent
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * CameraX implementation: Preview + VideoCapture<Recorder>. Requested quality and frame rate
 * are validated against the device; unsupported values fall back to the nearest lower ones,
 * and a bind that still fails is retried with a conservative 1080p/30 configuration.
 * The recorded video is not mirrored, so text in the scene reads correctly.
 */
class CameraXController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) : CameraController {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    override val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var meteringFactory: SurfaceOrientedMeteringPointFactory? = null

    override suspend fun bind(
        owner: LifecycleOwner,
        lens: CameraLens,
        resolution: VideoResolution,
        frameRate: Int,
    ): AppResult<BoundCamera> = try {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context).also { provider = it }
        val lenses = CameraLens.entries.filter { cameraProvider.hasCamera(it.selector()) }.toSet()
        if (lenses.isEmpty()) throw IllegalStateException("No camera available")
        val actualLens = if (lens in lenses) lens else lenses.first()
        val selector = actualLens.selector()
        val cameraInfo = cameraProvider.getCameraInfo(selector)

        val qualities = Recorder.getVideoCapabilities(cameraInfo).getSupportedQualities(DynamicRange.SDR)
        val resolutions = VideoResolution.entries.filter { it.quality() in qualities }.toSet()
            .ifEmpty { setOf(VideoResolution.HD_720) }
        val actualResolution = resolutions.filter { it.height <= resolution.height }.maxByOrNull { it.height }
            ?: resolutions.minBy { it.height }

        val frameRates = RecordingSettings.FrameRates.filter { fps ->
            cameraInfo.supportedFrameRateRanges.any { fps in it.lower..it.upper }
        }.toSet().ifEmpty { setOf(30) }
        val actualFps = frameRates.filter { it <= frameRate }.maxOrNull() ?: frameRates.min()

        val (finalResolution, finalFps) = try {
            bindUseCases(cameraProvider, owner, selector, actualResolution, actualFps)
            actualResolution to actualFps
        } catch (e: IllegalArgumentException) {
            // Some devices reject high frame rates or 4K together with preview: retry safely.
            logger.w(TAG, "Bind failed with ${actualResolution.name}@$actualFps, retrying at 1080p/30", e)
            bindUseCases(cameraProvider, owner, selector, VideoResolution.FHD_1080, 30)
            VideoResolution.FHD_1080 to 30
        }
        val boundCamera = checkNotNull(camera)
        val exposure = boundCamera.cameraInfo.exposureState
        val zoom = boundCamera.cameraInfo.zoomState.value
        _zoomRatio.value = zoom?.zoomRatio ?: 1f

        AppResult.Success(
            BoundCamera(
                lens = actualLens,
                resolution = finalResolution,
                frameRate = finalFps,
                capabilities = CameraCapabilities(
                    availableLenses = lenses,
                    resolutions = resolutions,
                    frameRates = frameRates,
                    hasTorch = boundCamera.cameraInfo.hasFlashUnit(),
                    exposureRange = if (exposure.isExposureCompensationSupported) {
                        exposure.exposureCompensationRange.lower..exposure.exposureCompensationRange.upper
                    } else {
                        0..0
                    },
                    exposureStep = exposure.exposureCompensationStep.toFloat(),
                    minZoom = zoom?.minZoomRatio ?: 1f,
                    maxZoom = zoom?.maxZoomRatio ?: 1f,
                ),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.e(TAG, "Unable to open the camera", e)
        AppResult.Failure(AppError.CameraUnavailable(e))
    }

    private fun bindUseCases(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        selector: CameraSelector,
        resolution: VideoResolution,
        fps: Int,
    ) {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(resolution.quality(), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val capture = VideoCapture.Builder(recorder)
            .setTargetFrameRate(Range(fps, fps))
            .build()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                _surfaceRequest.value = request
                meteringFactory = SurfaceOrientedMeteringPointFactory(
                    request.resolution.width.toFloat(),
                    request.resolution.height.toFloat(),
                )
            }
        }
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(owner, selector, preview, capture)
        videoCapture = capture
    }

    override fun unbind() {
        recording?.stop()
        recording = null
        provider?.unbindAll()
        camera = null
        videoCapture = null
        _surfaceRequest.value = null
    }

    override fun setZoomRatio(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
        _zoomRatio.value = clamped
    }

    override fun focusAt(surfaceX: Float, surfaceY: Float) {
        val point = meteringFactory?.createPoint(surfaceX, surfaceY) ?: return
        val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    override fun setExposureIndex(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    override fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    @SuppressLint("MissingPermission") // Audio is only enabled after checking the permission.
    override fun startRecording(filePath: String, onEvent: (RecorderEvent) -> Unit): AppResult<Unit> {
        val capture = videoCapture ?: return AppResult.Failure(AppError.CameraUnavailable())
        if (recording != null) return AppResult.Success(Unit)
        return try {
            val file = File(filePath)
            val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            val withAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (withAudio) pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event -> handle(event, file, onEvent) }
            AppResult.Success(Unit)
        } catch (e: Throwable) {
            logger.e(TAG, "Unable to start recording", e)
            AppResult.Failure(AppError.RecordingFailed(e))
        }
    }

    private fun handle(event: VideoRecordEvent, file: File, onEvent: (RecorderEvent) -> Unit) {
        val elapsedMs = event.recordingStats.recordedDurationNanos / 1_000_000
        when (event) {
            is VideoRecordEvent.Status -> onEvent(RecorderEvent.Progress(elapsedMs))
            is VideoRecordEvent.Pause -> onEvent(RecorderEvent.Paused(elapsedMs))
            is VideoRecordEvent.Resume -> onEvent(RecorderEvent.Resumed(elapsedMs))
            is VideoRecordEvent.Finalize -> {
                recording = null
                // These errors still leave a playable file; anything else means no usable video.
                val recoverable = event.error in setOf(
                    VideoRecordEvent.Finalize.ERROR_NONE,
                    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
                    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                )
                val error = when {
                    recoverable && file.length() > 0 -> null
                    event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> AppError.StorageFull
                    else -> AppError.RecordingFailed(event.cause)
                }
                if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) logger.w(TAG, "Recording finalized with error ${event.error}", event.cause)
                val (width, height) = if (error == null) displaySize(file) else 0 to 0
                onEvent(RecorderEvent.Finished(elapsedMs, width, height, error))
            }
            else -> Unit
        }
    }

    override fun pauseRecording() {
        recording?.pause()
    }

    override fun resumeRecording() {
        recording?.resume()
    }

    override fun stopRecording() {
        recording?.stop()
    }

    /** Width/height as displayed (rotation applied), read from the finished file. */
    private fun displaySize(file: File): Pair<Int, Int> = runCatching {
        // Not `use {}`: MediaMetadataRetriever is only AutoCloseable from API 29.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation % 180 == 0) w to h else h to w
        } finally {
            retriever.release()
        }
    }.getOrDefault(0 to 0)

    private fun CameraLens.selector(): CameraSelector = when (this) {
        CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun VideoResolution.quality(): Quality = when (this) {
        VideoResolution.HD_720 -> Quality.HD
        VideoResolution.FHD_1080 -> Quality.FHD
        VideoResolution.UHD_2160 -> Quality.UHD
    }

    private companion object {
        const val TAG = "Camera"
    }
}
