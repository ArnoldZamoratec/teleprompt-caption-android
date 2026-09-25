package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import com.arnoldcode.glassprompt.domain.repository.TranscriptionScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeCaptionRepository : CaptionRepository {
    private val tracks = MutableStateFlow<Map<String, CaptionTrack>>(emptyMap())
    var saveCount = 0
        private set

    fun track(takeId: String): CaptionTrack? = tracks.value[takeId]

    fun seed(track: CaptionTrack) = tracks.update { it + (track.takeId to track) }

    override fun observeTrack(takeId: String): Flow<CaptionTrack?> = tracks.map { it[takeId] }

    override suspend fun getTrack(takeId: String): CaptionTrack? = tracks.value[takeId]

    override suspend fun saveTrack(track: CaptionTrack): AppResult<Unit> {
        saveCount++
        tracks.update { it + (track.takeId to track) }
        return AppResult.Success(Unit)
    }

    override suspend fun saveStyle(takeId: String, style: CaptionStyle): AppResult<Unit> {
        val track = tracks.value[takeId] ?: return AppResult.Failure(AppError.NotFound(takeId))
        saveCount++
        tracks.update { it + (takeId to track.copy(style = style)) }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteTrack(takeId: String): AppResult<Unit> {
        tracks.update { it - takeId }
        return AppResult.Success(Unit)
    }
}

/** Records requests; tests drive the state by hand. */
class FakeTranscriptionScheduler : TranscriptionScheduler {
    val states = MutableStateFlow<Map<String, TranscriptionState>>(emptyMap())
    val enqueued = mutableListOf<Triple<String, TranscriptionMode, Boolean>>()

    fun set(takeId: String, state: TranscriptionState) = states.update { it + (takeId to state) }

    override fun enqueue(takeId: String, mode: TranscriptionMode, replace: Boolean) {
        enqueued += Triple(takeId, mode, replace)
        set(takeId, TranscriptionState.Running(TranscriptionState.Stage.EXTRACTING_AUDIO, 0f))
    }

    override fun observe(takeId: String): Flow<TranscriptionState> = states.map { it[takeId] ?: TranscriptionState.Idle }

    override fun cancel(takeId: String) = set(takeId, TranscriptionState.Idle)
}
