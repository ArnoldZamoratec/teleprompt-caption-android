package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import kotlinx.coroutines.flow.Flow

interface CaptionRepository {
    /** The take's caption track, or null until it has been transcribed. */
    fun observeTrack(takeId: String): Flow<CaptionTrack?>
    suspend fun getTrack(takeId: String): CaptionTrack?

    /** Replaces the track (captions and style) in one transaction. */
    suspend fun saveTrack(track: CaptionTrack): AppResult<Unit>
    suspend fun saveStyle(takeId: String, style: CaptionStyle): AppResult<Unit>
    suspend fun deleteTrack(takeId: String): AppResult<Unit>
}

/** Runs transcriptions in the background so they survive leaving the screen. */
interface TranscriptionScheduler {
    /** Starts transcribing [takeId] unless it is already running. [replace] restarts it. */
    fun enqueue(takeId: String, mode: TranscriptionMode, replace: Boolean = false)
    fun observe(takeId: String): Flow<TranscriptionState>
    fun cancel(takeId: String)
}
