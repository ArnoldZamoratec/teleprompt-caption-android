package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import com.arnoldcode.glassprompt.domain.repository.VideoThumbnails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reserves a file for a new recording, failing early when storage is nearly full. */
class PrepareRecordingUseCase @Inject constructor(
    private val storage: MediaStorage,
) {
    operator fun invoke(): AppResult<String> =
        if (storage.availableBytes() < MediaStorage.MIN_FREE_BYTES_TO_RECORD) {
            AppResult.Failure(AppError.StorageFull)
        } else {
            AppResult.Success(storage.newTakeFile())
        }
}

/** Registers a finished recording; an empty/failed recording's file is cleaned up instead. */
class SaveTakeUseCase @Inject constructor(
    private val takes: TakeRepository,
    private val storage: MediaStorage,
) {
    suspend operator fun invoke(take: NewTake): AppResult<String> {
        if (take.durationMs < MIN_DURATION_MS) {
            storage.delete(take.filePath)
            return AppResult.Failure(AppError.RecordingFailed())
        }
        return takes.addTake(take)
    }

    /** Discards the file of a recording that failed or was cancelled. */
    fun discard(filePath: String) {
        storage.delete(filePath)
    }

    companion object {
        const val MIN_DURATION_MS = 500L
    }
}

class ObserveTakeUseCase @Inject constructor(
    private val takes: TakeRepository,
) {
    operator fun invoke(id: String): Flow<Take?> = takes.observeTake(id)
}

class ObserveProjectTakesUseCase @Inject constructor(
    private val takes: TakeRepository,
) {
    operator fun invoke(projectId: String): Flow<List<Take>> = takes.observeTakes(projectId)
}

class DeleteTakeUseCase @Inject constructor(
    private val takes: TakeRepository,
    private val thumbnails: VideoThumbnails,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> =
        takes.deleteTake(id).also { if (it is AppResult.Success) thumbnails.delete(takeThumbnailKey(id)) }
}

/** Persists teleprompter tweaks made while rehearsing or recording (speed, size, mirror…). */
class UpdateTeleprompterSettingsUseCase @Inject constructor(
    private val projects: ProjectRepository,
) {
    suspend operator fun invoke(project: Project, settings: TeleprompterSettings): AppResult<Unit> =
        if (project.teleprompter == settings) AppResult.Success(Unit) else projects.updateProject(project.copy(teleprompter = settings))
}
