package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.model.NewExport
import com.arnoldcode.glassprompt.domain.repository.ExportOutput
import com.arnoldcode.glassprompt.domain.repository.ExportRepository
import com.arnoldcode.glassprompt.domain.repository.ExportRequest
import com.arnoldcode.glassprompt.domain.repository.ExportScheduler
import com.arnoldcode.glassprompt.domain.repository.VideoExporter
import com.arnoldcode.glassprompt.domain.repository.VideoGallery
import com.arnoldcode.glassprompt.domain.repository.VideoThumbnails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeExportRepository(private val projectName: (takeId: String) -> String = { "Demo" }) : ExportRepository {
    private val exports = MutableStateFlow<Map<String, ExportedVideo>>(emptyMap())
    private var nextId = 1
    var now = 10_000L

    fun all(): List<ExportedVideo> = exports.value.values.toList()

    override fun observeRecent(limit: Int): Flow<List<ExportedVideo>> =
        exports.map { all -> all.values.sortedByDescending { it.createdAt }.take(limit) }

    override fun observeExport(id: String): Flow<ExportedVideo?> = exports.map { it[id] }

    override suspend fun addExport(export: NewExport): AppResult<String> {
        val id = "e${nextId++}"
        val video = ExportedVideo(
            id, export.takeId, "p1", projectName(export.takeId), export.filePath, export.width, export.height,
            export.frameRate, export.durationMs, export.sizeBytes, export.withCaptions, null, now++,
        )
        exports.update { it + (id to video) }
        return AppResult.Success(id)
    }

    override suspend fun setGalleryUri(id: String, uri: String): AppResult<Unit> {
        val export = exports.value[id] ?: return AppResult.Failure(AppError.NotFound(id))
        exports.update { it + (id to export.copy(galleryUri = uri)) }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteExport(id: String): AppResult<Unit> {
        if (id !in exports.value) return AppResult.Failure(AppError.NotFound(id))
        exports.update { it - id }
        return AppResult.Success(Unit)
    }
}

/** Records requests and reports progress; [failWith] makes it throw instead. */
class FakeVideoExporter : VideoExporter {
    val requests = mutableListOf<ExportRequest>()
    var failWith: Exception? = null

    override suspend fun export(request: ExportRequest, onProgress: (Float) -> Unit): ExportOutput {
        requests += request
        failWith?.let { throw it }
        onProgress(0.5f)
        onProgress(1f)
        return ExportOutput(width = 1080, height = 1920, sizeBytes = 1_000)
    }
}

class FakeVideoGallery : VideoGallery {
    val saved = mutableListOf<String>()

    override suspend fun save(filePath: String, displayName: String): AppResult<String> {
        saved += displayName
        return AppResult.Success("content://media/video/${saved.size}")
    }
}

class FakeExportScheduler : ExportScheduler {
    val states = MutableStateFlow<Map<String, ExportState>>(emptyMap())
    val started = mutableListOf<Pair<String, ExportSettings>>()
    val cleared = mutableListOf<String>()

    fun set(takeId: String, state: ExportState) = states.update { it + (takeId to state) }

    override fun enqueue(takeId: String, settings: ExportSettings) {
        started += takeId to settings
        set(takeId, ExportState.Running(0f, null))
    }

    override fun observe(takeId: String): Flow<ExportState> = states.map { it[takeId] ?: ExportState.Idle }

    override fun cancel(takeId: String) = set(takeId, ExportState.Idle)

    override fun clear(takeId: String) {
        cleared += takeId
    }
}

class FakeVideoThumbnails : VideoThumbnails {
    val requested = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun thumbnail(videoPath: String, key: String): String {
        requested += key
        return "/thumbs/$key.jpg"
    }

    override fun delete(key: String) {
        deleted += key
    }
}
