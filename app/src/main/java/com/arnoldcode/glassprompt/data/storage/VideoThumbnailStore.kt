package com.arnoldcode.glassprompt.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.scale
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.repository.VideoThumbnails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Small JPEG posters in cacheDir/thumbnails, made once per video and reused across screens and
 * launches. A poster older than its video is rebuilt; the system may clear the cache, in which
 * case posters are simply made again.
 */
@Singleton
class VideoThumbnailStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : VideoThumbnails {

    /** One lock per poster, so two screens asking at once decode the video only once. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun thumbnail(videoPath: String, key: String): String? = withContext(dispatchers.io) {
        val video = File(videoPath)
        if (!video.exists()) return@withContext null
        val target = File(dir(), "$key.jpg")
        locks.getOrPut(key) { Mutex() }.withLock {
            if (target.exists() && target.lastModified() >= video.lastModified()) return@withLock target.absolutePath
            runCatching { render(video, target) }
                .onFailure { logger.w(TAG, "Thumbnail failed for ${video.name}", it) }
                .getOrNull()
        }
    }

    override fun delete(key: String) {
        File(dir(), "$key.jpg").delete()
    }

    private fun render(video: File, target: File): String? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // A frame from the first second, not frame 0 (often black or mid-blink).
            val atUs = minOf(1_000L, durationMs / 10) * 1_000
            val frame = frameAt(retriever, atUs) ?: return null
            val poster = if (frame.width > MAX_WIDTH) {
                frame.scale(MAX_WIDTH, (frame.height * MAX_WIDTH / frame.width.toFloat()).roundToInt()).also { frame.recycle() }
            } else {
                frame
            }
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.outputStream().use { poster.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            poster.recycle()
            // Atomic: a half-written poster is never shown.
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            return target.absolutePath
        } finally {
            retriever.release()
        }
    }

    /** Scaled decode where available (API 27+), so a 4K frame never lands in memory at full size. */
    private fun frameAt(retriever: MediaMetadataRetriever, atUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_WIDTH, MAX_WIDTH * 2)
        } else {
            retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    private fun dir() = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    private companion object {
        const val TAG = "Thumbnails"
        const val MAX_WIDTH = 360
        const val JPEG_QUALITY = 80
    }
}
