package com.arnoldcode.glassprompt.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Reason
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Stage
import com.arnoldcode.glassprompt.domain.repository.TranscriptionScheduler
import com.arnoldcode.glassprompt.domain.usecase.TranscribeTakeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Runs [TranscribeTakeUseCase] outside any screen, reporting stage and progress as work progress. */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcribe: TranscribeTakeUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val takeId = inputData.getString(KEY_TAKE_ID) ?: return Result.failure(reasonData(Reason.NOT_FOUND))
        val mode = inputData.getString(KEY_MODE)?.let { name -> TranscriptionMode.entries.firstOrNull { it.name == name } }
            ?: TranscriptionMode.AUTO

        var lastReported = -1
        val state = transcribe(takeId, mode) { stage, progress ->
            // Progress writes go through the WorkManager database: only report visible changes.
            val percent = (progress * 100).toInt()
            val key = stage.ordinal * 1000 + percent
            if (key != lastReported) {
                lastReported = key
                setProgressAsync(workDataOf(KEY_STAGE to stage.name, KEY_PROGRESS to progress))
            }
        }
        return when (state) {
            is TranscriptionState.Failed -> Result.failure(reasonData(state.reason))
            else -> Result.success()
        }
    }

    private fun reasonData(reason: Reason): Data = workDataOf(KEY_REASON to reason.name)

    companion object {
        const val KEY_TAKE_ID = "takeId"
        const val KEY_MODE = "mode"
        const val KEY_STAGE = "stage"
        const val KEY_PROGRESS = "progress"
        const val KEY_REASON = "reason"

        fun uniqueName(takeId: String) = "transcribe-$takeId"
    }
}

@Singleton
class WorkManagerTranscriptionScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TranscriptionScheduler {

    // Lazy: WorkManager is initialised on demand through the app's Configuration.Provider.
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun enqueue(takeId: String, mode: TranscriptionMode, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_TAKE_ID to takeId, TranscriptionWorker.KEY_MODE to mode.name))
            .addTag(TAG)
            .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(TranscriptionWorker.uniqueName(takeId), policy, request)
    }

    override fun observe(takeId: String): Flow<TranscriptionState> =
        workManager.getWorkInfosForUniqueWorkFlow(TranscriptionWorker.uniqueName(takeId))
            .map { infos -> infos.lastOrNull()?.toState() ?: TranscriptionState.Idle }
            .distinctUntilChanged()

    override fun cancel(takeId: String) {
        workManager.cancelUniqueWork(TranscriptionWorker.uniqueName(takeId))
    }

    private fun WorkInfo.toState(): TranscriptionState = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TranscriptionState.Running(Stage.EXTRACTING_AUDIO, 0f)
        WorkInfo.State.RUNNING -> TranscriptionState.Running(
            stage = progress.getString(TranscriptionWorker.KEY_STAGE)
                ?.let { name -> Stage.entries.firstOrNull { it.name == name } }
                ?: Stage.EXTRACTING_AUDIO,
            progress = progress.getFloat(TranscriptionWorker.KEY_PROGRESS, 0f),
        )
        WorkInfo.State.SUCCEEDED -> TranscriptionState.Done
        WorkInfo.State.FAILED -> TranscriptionState.Failed(
            outputData.getString(TranscriptionWorker.KEY_REASON)
                ?.let { name -> Reason.entries.firstOrNull { it.name == name } }
                ?: Reason.ENGINE_ERROR,
        )
        WorkInfo.State.CANCELLED -> TranscriptionState.Idle
    }

    private companion object {
        const val TAG = "transcription"
    }
}
