package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.ExportOptions
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.ExportState.Reason
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.model.NewExport
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import com.arnoldcode.glassprompt.domain.repository.ExportRepository
import com.arnoldcode.glassprompt.domain.repository.ExportRequest
import com.arnoldcode.glassprompt.domain.repository.ExportScheduler
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import com.arnoldcode.glassprompt.domain.repository.VideoExporter
import com.arnoldcode.glassprompt.domain.repository.VideoGallery
import com.arnoldcode.glassprompt.domain.repository.VideoThumbnails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Take → MP4: checks free space against the estimated size, renders (captions burned in when
 * asked and available), then records the export. A failed or cancelled render leaves no file.
 */
class ExportVideoUseCase @Inject constructor(
    private val takes: TakeRepository,
    private val captions: CaptionRepository,
    private val exports: ExportRepository,
    private val exporter: VideoExporter,
    private val storage: MediaStorage,
    private val logger: Logger,
) {

    suspend operator fun invoke(takeId: String, settings: ExportSettings, onProgress: (Float) -> Unit): ExportState {
        val take = takes.getTake(takeId).getOrNull() ?: return ExportState.Failed(Reason.NOT_FOUND)
        val (width, height) = ExportOptions.outputSize(take.width, take.height, settings.resolution)
        val frameRate = minOf(settings.frameRate, take.frameRate)
        val estimate = ExportOptions.estimateBytes(width, height, frameRate, take.durationMs)
        if (storage.availableBytes() < estimate + SAFETY_MARGIN_BYTES) return ExportState.Failed(Reason.STORAGE_FULL)

        val track = if (settings.burnCaptions) captions.getTrack(takeId)?.takeIf { it.captions.isNotEmpty() } else null
        val output = storage.newExportFile()
        val result = try {
            exporter.export(ExportRequest(take, track, settings.copy(frameRate = frameRate), output), onProgress)
        } catch (e: CancellationException) {
            storage.delete(output)
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Export failed", e)
            storage.delete(output)
            return ExportState.Failed(Reason.ENCODER)
        }

        val saved = exports.addExport(
            NewExport(
                takeId = takeId,
                filePath = output,
                width = result.width,
                height = result.height,
                frameRate = frameRate,
                durationMs = take.durationMs,
                sizeBytes = result.sizeBytes,
                withCaptions = track != null,
            ),
        )
        return when (saved) {
            is AppResult.Success -> ExportState.Done(saved.data)
            is AppResult.Failure -> {
                storage.delete(output)
                ExportState.Failed(Reason.UNKNOWN)
            }
        }
    }

    private companion object {
        const val TAG = "Export"
        const val SAFETY_MARGIN_BYTES = 50L * 1024 * 1024
    }
}

/** Starts, follows and cancels background exports. */
class ExportControlUseCase @Inject constructor(private val scheduler: ExportScheduler) {
    fun start(takeId: String, settings: ExportSettings) = scheduler.enqueue(takeId, settings)
    fun observe(takeId: String): Flow<ExportState> = scheduler.observe(takeId)
    fun cancel(takeId: String) = scheduler.cancel(takeId)
    fun clear(takeId: String) = scheduler.clear(takeId)
}

class ObserveExportUseCase @Inject constructor(private val exports: ExportRepository) {
    operator fun invoke(id: String): Flow<ExportedVideo?> = exports.observeExport(id)
}

class ObserveRecentExportsUseCase @Inject constructor(private val exports: ExportRepository) {
    operator fun invoke(limit: Int): Flow<List<ExportedVideo>> = exports.observeRecent(limit)
}

class DeleteExportUseCase @Inject constructor(
    private val exports: ExportRepository,
    private val thumbnails: VideoThumbnails,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> =
        exports.deleteExport(id).also { if (it is AppResult.Success) thumbnails.delete(exportThumbnailKey(id)) }
}

/** Cache key of an export's poster. */
fun exportThumbnailKey(exportId: String) = "export_$exportId"

/** Cache key of a take's poster. */
fun takeThumbnailKey(takeId: String) = "take_$takeId"

class VideoThumbnailUseCase @Inject constructor(private val thumbnails: VideoThumbnails) {
    suspend operator fun invoke(videoPath: String, key: String): String? = thumbnails.thumbnail(videoPath, key)
}

/** Copies an export to the gallery once; later calls return the existing copy. */
class SaveToGalleryUseCase @Inject constructor(
    private val exports: ExportRepository,
    private val gallery: VideoGallery,
) {
    suspend operator fun invoke(export: ExportedVideo): AppResult<String> {
        export.galleryUri?.let { return AppResult.Success(it) }
        val displayName = "GlassPrompt_${export.projectName.toFileName()}_${export.createdAt}.mp4"
        return when (val saved = gallery.save(export.filePath, displayName)) {
            is AppResult.Success -> {
                exports.setGalleryUri(export.id, saved.data)
                saved
            }
            is AppResult.Failure -> saved
        }
    }

    private fun String.toFileName(): String =
        replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifEmpty { "video" }
}
