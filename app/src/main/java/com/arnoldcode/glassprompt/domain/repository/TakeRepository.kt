package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.Take
import kotlinx.coroutines.flow.Flow

interface TakeRepository {
    fun observeTakes(projectId: String): Flow<List<Take>>
    fun observeTake(id: String): Flow<Take?>
    suspend fun getTake(id: String): AppResult<Take>
    suspend fun addTake(take: NewTake): AppResult<String>

    /** Deletes the take row and its video file. */
    suspend fun deleteTake(id: String): AppResult<Unit>
}

/** App-private media files. Paths are absolute and never leave the app except through sharing. */
interface MediaStorage {
    /** A fresh, not-yet-existing file for a new recording. */
    fun newTakeFile(): String

    /** A fresh, not-yet-existing file for an export. */
    fun newExportFile(): String

    /** Bytes available for new recordings. */
    fun availableBytes(): Long

    fun delete(path: String): Boolean

    companion object {
        /** Refuse to start recording below this (≈ 1 min of 4K). */
        const val MIN_FREE_BYTES_TO_RECORD = 400L * 1024 * 1024
    }
}
