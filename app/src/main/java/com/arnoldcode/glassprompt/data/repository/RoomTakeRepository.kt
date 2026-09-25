package com.arnoldcode.glassprompt.data.repository

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.data.local.dao.TakeDao
import com.arnoldcode.glassprompt.data.local.entities.TakeEntity
import com.arnoldcode.glassprompt.data.local.entities.toDomain
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTakeRepository @Inject constructor(
    private val dao: TakeDao,
    private val storage: MediaStorage,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val logger: Logger,
) : TakeRepository {

    override fun observeTakes(projectId: String): Flow<List<Take>> =
        dao.observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeTake(id: String): Flow<Take?> =
        dao.observe(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getTake(id: String): AppResult<Take> {
        val take = (safeCall(logger, TAG) { dao.get(id) } as? AppResult.Success)?.data
        return take?.let { AppResult.Success(it.toDomain()) } ?: AppResult.Failure(AppError.NotFound("take $id"))
    }

    override suspend fun addTake(take: NewTake): AppResult<String> = safeCall(logger, TAG) {
        val entity = TakeEntity(
            id = ids.newId(),
            projectId = take.projectId,
            filePath = take.filePath,
            durationMs = take.durationMs,
            width = take.width,
            height = take.height,
            frameRate = take.frameRate,
            createdAt = clock.millis(),
        )
        dao.insert(entity)
        entity.id
    }

    override suspend fun deleteTake(id: String): AppResult<Unit> {
        val take = dao.get(id) ?: return AppResult.Failure(AppError.NotFound("take $id"))
        return safeCall(logger, TAG) {
            dao.delete(id)
            storage.delete(take.filePath)
            Unit
        }
    }

    private companion object {
        const val TAG = "Takes"
    }
}
