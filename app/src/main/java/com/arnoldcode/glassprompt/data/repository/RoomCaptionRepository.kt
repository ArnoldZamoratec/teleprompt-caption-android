package com.arnoldcode.glassprompt.data.repository

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.data.local.dao.CaptionDao
import com.arnoldcode.glassprompt.data.local.entities.CaptionEntity
import com.arnoldcode.glassprompt.data.local.entities.CaptionJson
import com.arnoldcode.glassprompt.data.local.entities.CaptionTrackEntity
import com.arnoldcode.glassprompt.data.local.entities.toDomain
import com.arnoldcode.glassprompt.data.local.entities.toEntity
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCaptionRepository @Inject constructor(
    private val dao: CaptionDao,
    private val clock: Clock,
    private val logger: Logger,
) : CaptionRepository {

    override fun observeTrack(takeId: String): Flow<CaptionTrack?> =
        combine(dao.observeTrack(takeId), dao.observeCaptions(takeId)) { track, captions -> track?.toDomain(captions) }
            .distinctUntilChanged()

    override suspend fun getTrack(takeId: String): CaptionTrack? {
        val track = dao.getTrack(takeId) ?: return null
        return track.toDomain(dao.getCaptions(takeId))
    }

    override suspend fun saveTrack(track: CaptionTrack): AppResult<Unit> = safeCall(logger, TAG) {
        dao.replaceTrack(
            CaptionTrackEntity(
                takeId = track.takeId,
                styleJson = CaptionJson.encodeStyle(track.style),
                language = track.language,
                engineId = track.engineId,
                updatedAt = clock.millis(),
            ),
            track.captions.map { it.toEntity(track.takeId) },
        )
    }

    override suspend fun saveStyle(takeId: String, style: CaptionStyle): AppResult<Unit> {
        val updated = safeCall(logger, TAG) { dao.updateStyle(takeId, CaptionJson.encodeStyle(style), clock.millis()) }
        return when {
            updated is AppResult.Failure -> updated
            (updated as AppResult.Success).data == 0 -> AppResult.Failure(AppError.NotFound("caption track $takeId"))
            else -> AppResult.Success(Unit)
        }
    }

    override suspend fun deleteTrack(takeId: String): AppResult<Unit> = safeCall(logger, TAG) { dao.deleteTrack(takeId) }

    private fun CaptionTrackEntity.toDomain(captions: List<CaptionEntity>) = CaptionTrack(
        takeId = takeId,
        captions = captions.map { it.toDomain() },
        style = CaptionJson.decodeStyle(styleJson),
        language = language,
        engineId = engineId,
    )

    private companion object {
        const val TAG = "Captions"
    }
}
