package com.arnoldcode.glassprompt.data.repository

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.data.local.dao.ExportDao
import com.arnoldcode.glassprompt.data.local.entities.ExportEntity
import com.arnoldcode.glassprompt.data.local.entities.toDomain
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.model.NewExport
import com.arnoldcode.glassprompt.domain.repository.ExportRepository
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExportRepository @Inject constructor(
    private val dao: ExportDao,
    private val storage: MediaStorage,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val logger: Logger,
) : ExportRepository {

    override fun observeRecent(limit: Int): Flow<List<ExportedVideo>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeExport(id: String): Flow<ExportedVideo?> =
        dao.observe(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun addExport(export: NewExport): AppResult<String> = safeCall(logger, TAG) {
        val entity = ExportEntity(
            id = ids.newId(),
            takeId = export.takeId,
            filePath = export.filePath,
            width = export.width,
            height = export.height,
            frameRate = export.frameRate,
            durationMs = export.durationMs,
            sizeBytes = export.sizeBytes,
            withCaptions = export.withCaptions,
            galleryUri = null,
            createdAt = clock.millis(),
        )
        dao.insert(entity)
        entity.id
    }

    override suspend fun setGalleryUri(id: String, uri: String): AppResult<Unit> {
        val updated = safeCall(logger, TAG) { dao.setGalleryUri(id, uri) }
        return when {
            updated is AppResult.Failure -> updated
            (updated as AppResult.Success).data == 0 -> AppResult.Failure(AppError.NotFound("export $id"))
            else -> AppResult.Success(Unit)
        }
    }

    override suspend fun deleteExport(id: String): AppResult<Unit> {
        val export = dao.get(id) ?: return AppResult.Failure(AppError.NotFound("export $id"))
        return safeCall(logger, TAG) {
            dao.delete(id)
            storage.delete(export.filePath)
            Unit
        }
    }

    private companion object {
        const val TAG = "Exports"
    }
}
