package com.arnoldcode.glassprompt.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arnoldcode.glassprompt.MainActivity
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.ExportState.Reason
import com.arnoldcode.glassprompt.domain.model.RemainingTimeEstimator
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.repository.ExportScheduler
import com.arnoldcode.glassprompt.domain.usecase.ExportVideoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a take in a foreground service so it keeps going with the screen off or the app in
 * the background. The notification shows progress, remaining time and a cancel action.
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exportVideo: ExportVideoUseCase,
) : CoroutineWorker(context, params) {

    private val notifications = ExportNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0f, null)

    override suspend fun doWork(): Result {
        val takeId = inputData.getString(KEY_TAKE_ID) ?: return Result.failure(workDataOf(KEY_REASON to Reason.NOT_FOUND.name))
        val settings = ExportSettings(
            resolution = inputData.getString(KEY_RESOLUTION)?.let { name -> VideoResolution.entries.firstOrNull { it.name == name } }
                ?: VideoResolution.FHD_1080,
            frameRate = inputData.getInt(KEY_FRAME_RATE, 30),
            burnCaptions = inputData.getBoolean(KEY_CAPTIONS, true),
        )
        setForeground(foregroundInfo(0f, null))

        val estimator = RemainingTimeEstimator()
        var lastPercent = -1
        val state = exportVideo(takeId, settings) { progress ->
            val percent = (progress * 100).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                val remaining = estimator.update(System.currentTimeMillis(), progress)
                setProgressAsync(workDataOf(KEY_PROGRESS to progress, KEY_REMAINING to (remaining ?: -1L)))
                notifications.progress(id, progress, remaining)
            }
        }
        return when (state) {
            is ExportState.Done -> {
                notifications.finished(success = true)
                Result.success(workDataOf(KEY_EXPORT_ID to state.exportId))
            }
            is ExportState.Failed -> {
                notifications.finished(success = false)
                Result.failure(workDataOf(KEY_REASON to state.reason.name))
            }
            else -> Result.failure()
        }
    }

    private fun foregroundInfo(progress: Float, remainingMs: Long?): ForegroundInfo {
        val notification = notifications.build(id, progress, remainingMs)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ForegroundInfo(ExportNotifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(ExportNotifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(ExportNotifications.PROGRESS_ID, notification)
        }
    }

    companion object {
        const val KEY_TAKE_ID = "takeId"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_FRAME_RATE = "frameRate"
        const val KEY_CAPTIONS = "captions"
        const val KEY_PROGRESS = "progress"
        const val KEY_REMAINING = "remainingMs"
        const val KEY_EXPORT_ID = "exportId"
        const val KEY_REASON = "reason"

        fun uniqueName(takeId: String) = "export-$takeId"
    }
}

/** Notification channel and the progress / result notifications of exports. */
class ExportNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.export_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = context.getString(R.string.export_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(workId: UUID, progress: Float, remainingMs: Long?) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_export)
            .setContentTitle(context.getString(R.string.export_notification_title))
            .setContentText(remainingMs?.let { context.getString(R.string.export_remaining, formatRemaining(it)) })
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()

    fun progress(workId: UUID, progress: Float, remainingMs: Long?) = notify(PROGRESS_ID, build(workId, progress, remainingMs))

    fun finished(success: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_export)
            .setContentTitle(context.getString(if (success) R.string.export_notification_done else R.string.export_notification_failed))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        notify(DONE_ID, notification)
    }

    /** Silently skipped without the notification permission: the screen shows progress anyway. */
    private fun notify(id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(id, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "exports"
        const val PROGRESS_ID = 1001
        const val DONE_ID = 1002

        /** "1 min 20 s" / "45 s". */
        fun formatRemaining(ms: Long): String {
            val seconds = (ms + 999) / 1000
            return if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s" else "$seconds s"
        }
    }
}

@Singleton
class WorkManagerExportScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExportScheduler {

    private val workManager by lazy { WorkManager.getInstance(context) }

    /** Work ids the UI already consumed; WorkManager keeps finished work around for a while. */
    private val cleared = MutableStateFlow<Set<UUID>>(emptySet())

    /** Latest finished work seen per take, so [clear] needs no blocking WorkManager query. */
    private val lastFinished = ConcurrentHashMap<String, UUID>()

    override fun enqueue(takeId: String, settings: ExportSettings) {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    ExportWorker.KEY_TAKE_ID to takeId,
                    ExportWorker.KEY_RESOLUTION to settings.resolution.name,
                    ExportWorker.KEY_FRAME_RATE to settings.frameRate,
                    ExportWorker.KEY_CAPTIONS to settings.burnCaptions,
                ),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(ExportWorker.uniqueName(takeId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun observe(takeId: String): Flow<ExportState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(ExportWorker.uniqueName(takeId)), cleared) { infos, cleared ->
            val latest = infos.lastOrNull()
            if (latest != null && latest.state.isFinished) lastFinished[takeId] = latest.id
            latest?.takeIf { it.id !in cleared }?.toState() ?: ExportState.Idle
        }.distinctUntilChanged()

    override fun cancel(takeId: String) {
        workManager.cancelUniqueWork(ExportWorker.uniqueName(takeId))
    }

    override fun clear(takeId: String) {
        val finished = lastFinished[takeId] ?: return
        cleared.value = cleared.value + finished
    }

    private fun WorkInfo.toState(): ExportState = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportState.Running(0f, null)
        WorkInfo.State.RUNNING -> ExportState.Running(
            progress = progress.getFloat(ExportWorker.KEY_PROGRESS, 0f),
            remainingMs = progress.getLong(ExportWorker.KEY_REMAINING, -1L).takeIf { it >= 0 },
        )
        WorkInfo.State.SUCCEEDED -> outputData.getString(ExportWorker.KEY_EXPORT_ID)?.let { ExportState.Done(it) }
            ?: ExportState.Failed(Reason.UNKNOWN)
        WorkInfo.State.FAILED -> ExportState.Failed(
            outputData.getString(ExportWorker.KEY_REASON)?.let { name -> Reason.entries.firstOrNull { it.name == name } }
                ?: Reason.UNKNOWN,
        )
        WorkInfo.State.CANCELLED -> ExportState.Idle
    }

    private companion object {
        const val TAG = "export"
    }
}
