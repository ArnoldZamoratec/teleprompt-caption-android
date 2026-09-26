package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.model.NewExport
import com.arnoldcode.glassprompt.domain.model.Take
import kotlinx.coroutines.flow.Flow

interface ExportRepository {
    /** Newest first, across all projects. */
    fun observeRecent(limit: Int): Flow<List<ExportedVideo>>
    fun observeExport(id: String): Flow<ExportedVideo?>
    suspend fun addExport(export: NewExport): AppResult<String>
    suspend fun setGalleryUri(id: String, uri: String): AppResult<Unit>

    /** Deletes the row and its file (a gallery copy is the user's and stays). */
    suspend fun deleteExport(id: String): AppResult<Unit>
}

/** Everything needed to render one export. */
data class ExportRequest(
    val take: Take,
    val captions: CaptionTrack?,
    val settings: ExportSettings,
    val outputPath: String,
)

data class ExportOutput(val width: Int, val height: Int, val sizeBytes: Long)

/** Renders a take (plus burned-in captions) to an MP4. Implemented with Media3 Transformer. */
interface VideoExporter {
    /** Throws on failure; cancelling the coroutine cancels the export. */
    suspend fun export(request: ExportRequest, onProgress: (Float) -> Unit): ExportOutput
}

/** Runs exports in the background (foreground service with a notification). */
interface ExportScheduler {
    fun enqueue(takeId: String, settings: ExportSettings)
    fun observe(takeId: String): Flow<ExportState>
    fun cancel(takeId: String)

    /** Forgets a finished/failed export so the screen starts clean next time. */
    fun clear(takeId: String)
}

/** Copies a video into the shared gallery (Movies/GlassPrompt). */
interface VideoGallery {
    /** Returns the content:// URI of the copy. */
    suspend fun save(filePath: String, displayName: String): AppResult<String>
}

/** Writes a take's captions as a subtitle file others can open (YouTube, editors). */
interface SubtitleFiles {
    /** Path of a fresh .srt in a shareable cache folder; fails with EmptyContent without captions. */
    suspend fun writeSrt(takeId: String, baseName: String): AppResult<String>
}

/** Poster images for videos, generated once and cached on disk. */
interface VideoThumbnails {
    /** Path of a JPEG poster for [videoPath], made on first request; null if the video can't be read. */
    suspend fun thumbnail(videoPath: String, key: String): String?
    fun delete(key: String)
}
