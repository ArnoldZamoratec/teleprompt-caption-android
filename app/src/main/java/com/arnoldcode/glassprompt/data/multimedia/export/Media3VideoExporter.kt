package com.arnoldcode.glassprompt.data.multimedia.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.domain.model.ExportOptions
import com.arnoldcode.glassprompt.domain.model.VideoBitrates
import com.arnoldcode.glassprompt.domain.repository.ExportOutput
import com.arnoldcode.glassprompt.domain.repository.ExportRequest
import com.arnoldcode.glassprompt.domain.repository.VideoExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Media3 Transformer export: scale (short side = chosen resolution), drop frames down to the
 * chosen rate, burn in captions; H.264 + AAC in MP4. The encoder factory falls back to a
 * configuration the device supports instead of failing. Transformer is driven from the main
 * looper; progress is polled because Transformer only offers a pull API.
 */
@UnstableApi
class Media3VideoExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : VideoExporter {

    override suspend fun export(request: ExportRequest, onProgress: (Float) -> Unit): ExportOutput =
        withContext(dispatchers.main) {
            val take = request.take
            val (width, height) = ExportOptions.outputSize(take.width, take.height, request.settings.resolution)
            val effects = buildList<Effect> {
                add(Presentation.createForShortSide(minOf(width, height)))
                if (request.settings.frameRate < take.frameRate) {
                    add(FrameDropEffect.createDefaultFrameDropEffect(request.settings.frameRate.toFloat()))
                }
                request.captions?.let { track -> add(OverlayEffect(listOf(CaptionBitmapOverlay(track.captions, track.style)))) }
            }
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(take.filePath))))
                .setEffects(Effects(emptyList(), effects))
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(VideoBitrates.export(minOf(width, height), request.settings.frameRate))
                                .build(),
                        )
                        .setEnableFallback(true)
                        .build(),
                )
                .build()

            val poller = launch {
                val holder = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    // Cancellation can arrive on any thread; Transformer must be used from its looper.
                    cont.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
                    transformer.start(item, request.outputPath)
                }
            } finally {
                poller.cancel()
            }
            onProgress(1f)
            ExportOutput(width, height, File(request.outputPath).length())
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
