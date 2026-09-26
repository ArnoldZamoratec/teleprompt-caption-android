package com.arnoldcode.glassprompt.domain.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A finished export: an MP4 in app storage, optionally also copied to the gallery. */
data class ExportedVideo(
    val id: String,
    val takeId: String,
    val projectId: String,
    val projectName: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val withCaptions: Boolean,
    /** content:// URI once saved to the gallery. */
    val galleryUri: String?,
    val createdAt: Long,
)

data class NewExport(
    val takeId: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val withCaptions: Boolean,
)

/** What the user picked on the export screen. */
data class ExportSettings(
    val resolution: VideoResolution,
    val frameRate: Int,
    val burnCaptions: Boolean,
)

/** Progress of the background export of a take. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val remainingMs: Long?) : ExportState
    data class Done(val exportId: String) : ExportState
    data class Failed(val reason: Reason) : ExportState

    enum class Reason { NOT_FOUND, STORAGE_FULL, ENCODER, UNKNOWN }
}

/**
 * Honest export options: never more pixels or frames than the take has (no upscaling, no
 * invented frames), and sizes that keep the take's aspect ratio.
 */
object ExportOptions {

    val FrameRates = listOf(24, 30, 60)

    /** Resolutions whose short side does not exceed the take's; at least the smallest one. */
    fun resolutions(take: Take): List<VideoResolution> {
        val shortSide = min(take.width, take.height)
        return VideoResolution.entries.filter { it.height <= shortSide }.ifEmpty { listOf(VideoResolution.entries.first()) }
    }

    /** Frame rates not above the recorded one; the recorded rate itself if it is not a standard one. */
    fun frameRates(take: Take): List<Int> = FrameRates.filter { it <= take.frameRate }.ifEmpty { listOf(take.frameRate) }

    /** 1080p (or the best below it) at the recorded rate, captions on when there are any. */
    fun defaults(take: Take, hasCaptions: Boolean): ExportSettings {
        val resolutions = resolutions(take)
        val resolution = resolutions.lastOrNull { it.height <= VideoResolution.FHD_1080.height } ?: resolutions.first()
        return ExportSettings(resolution, frameRates(take).last(), burnCaptions = hasCaptions)
    }

    /**
     * Output size for [resolution]: short side = the resolution's height (never above the
     * source), long side scaled to keep the aspect ratio. Both even, as encoders require.
     */
    fun outputSize(sourceWidth: Int, sourceHeight: Int, resolution: VideoResolution): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return even(resolution.height * 9 / 16) to even(resolution.height)
        val shortSide = min(sourceWidth, sourceHeight)
        val targetShort = min(resolution.height, shortSide)
        val scale = targetShort.toDouble() / shortSide
        return even((sourceWidth * scale).roundToInt()) to even((sourceHeight * scale).roundToInt())
    }

    /** Size of a re-encoded export (H.264 at [VideoBitrates.export] + AAC), to warn before running out of space. */
    fun estimateBytes(width: Int, height: Int, frameRate: Int, durationMs: Long): Long {
        val bitsPerSecond = VideoBitrates.export(min(width, height), frameRate).toLong() + VideoBitrates.AUDIO
        return bitsPerSecond * durationMs / 1000 / 8
    }

    /**
     * True when the export changes nothing (same size and rate, no captions): Media3 then copies
     * the streams instead of re-encoding, so the result is exactly as big as the take.
     */
    fun isPassthrough(take: Take, settings: ExportSettings, burnsCaptions: Boolean): Boolean =
        !burnsCaptions &&
            settings.frameRate >= take.frameRate &&
            settings.resolution.height >= min(take.width, take.height)

    private fun even(value: Int) = max(2, value - value % 2)
}

/**
 * H.264 bitrates. Recording keeps headroom for later edits; exports use the rates YouTube
 * recommends for uploads, which is what social apps re-encode to anyway. Camera defaults on some
 * phones are ~17 Mbit/s at 1080p30, about 130 MB per minute, for no visible gain.
 */
object VideoBitrates {

    const val AUDIO = 128_000

    /** Bits per second for recording at [shortSide] (720, 1080, 2160) and [frameRate]. */
    fun recording(shortSide: Int, frameRate: Int): Int = scaled(
        when {
            shortSide <= 720 -> 6_000_000
            shortSide <= 1080 -> 10_000_000
            else -> 35_000_000
        },
        frameRate,
    )

    /** Bits per second for a re-encoded export. */
    fun export(shortSide: Int, frameRate: Int): Int = scaled(
        when {
            shortSide <= 720 -> 5_000_000
            shortSide <= 1080 -> 8_000_000
            else -> 35_000_000
        },
        frameRate,
    )

    /** High frame rates need about 1.5× the bits for the same quality. */
    private fun scaled(base: Int, frameRate: Int): Int = if (frameRate > 30) base * 3 / 2 else base
}

/**
 * Remaining-time estimate from progress samples: rate over a sliding window, so a slow start
 * (encoder warm-up) or a stall does not skew it for long. Null until there is enough signal.
 */
class RemainingTimeEstimator(private val windowMs: Long = 5_000) {
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun update(nowMs: Long, progress: Float): Long? {
        samples.addLast(nowMs to progress)
        while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
        val (t0, p0) = samples.first()
        val elapsed = nowMs - t0
        val advanced = progress - p0
        if (elapsed < MIN_ELAPSED_MS || advanced <= 0f) return null
        return ((1f - progress) / advanced * elapsed).toLong()
    }

    private companion object {
        const val MIN_ELAPSED_MS = 1_000L
    }
}
