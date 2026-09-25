package com.arnoldcode.glassprompt.domain.transcription

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Energy-based voice activity detection over fixed frames. The threshold adapts to the
 * recording's own noise floor, so it works in quiet rooms and noisy streets alike.
 */
object EnergyVad {

    const val FRAME_MS = 30

    /** Above the noise floor by this much counts as speech. */
    private const val MARGIN_DB = 9.0
    /** Never treat anything quieter than this as speech (dBFS). */
    private const val ABSOLUTE_FLOOR_DB = -52.0
    /**
     * Anything louder than this is speech even if the "noise floor" estimate is high — which
     * happens when someone talks through the whole take without pausing.
     */
    private const val ALWAYS_SPEECH_DB = -26.0
    /** Gaps shorter than this are bridged (breaths between words). */
    private const val HANGOVER_MS = 350L
    private const val MIN_REGION_MS = 200L
    private const val PAD_MS = 120L

    /**
     * Splits regions longer than [maxMs] at their quietest frame in the second half of each
     * [maxMs] window, so a recognizer that stops after one long utterance still hears it all,
     * and a cut rarely lands mid-word.
     */
    fun splitLong(regions: List<SpeechRegion>, frameDbs: DoubleArray, maxMs: Long): List<SpeechRegion> {
        val result = mutableListOf<SpeechRegion>()
        for (region in regions) {
            var start = region.startMs
            while (region.endMs - start > maxMs) {
                val fromFrame = ((start + maxMs / 2) / FRAME_MS).toInt()
                val toFrame = ((start + maxMs) / FRAME_MS).toInt().coerceAtMost(frameDbs.size)
                val quietest = (fromFrame until toFrame).minByOrNull { frameDbs[it] }
                val cut = quietest?.let { it.toLong() * FRAME_MS } ?: (start + maxMs)
                result += SpeechRegion(start, cut)
                start = cut
            }
            result += SpeechRegion(start, region.endMs)
        }
        return result
    }

    /** RMS level of a frame in dBFS (−96 for silence). */
    fun frameDb(samples: ShortArray, from: Int = 0, to: Int = samples.size): Double {
        if (to <= from) return -96.0
        var sum = 0.0
        for (i in from until to) {
            val v = samples[i] / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / (to - from))
        return if (rms <= 0.0) -96.0 else max(-96.0, 20 * log10(rms))
    }

    /** Speech regions from per-frame levels ([FRAME_MS] each), clipped to [durationMs]. */
    fun detect(frameDbs: DoubleArray, durationMs: Long): List<SpeechRegion> {
        if (frameDbs.isEmpty()) return emptyList()
        val sorted = frameDbs.sorted()
        val noiseFloor = sorted[(sorted.size * 0.2).toInt().coerceAtMost(sorted.lastIndex)]
        val threshold = min(max(noiseFloor + MARGIN_DB, ABSOLUTE_FLOOR_DB), ALWAYS_SPEECH_DB)

        val raw = mutableListOf<SpeechRegion>()
        var start = -1
        frameDbs.forEachIndexed { i, db ->
            val speech = db >= threshold
            if (speech && start < 0) start = i
            if (!speech && start >= 0) {
                raw += SpeechRegion(start * FRAME_MS.toLong(), i * FRAME_MS.toLong())
                start = -1
            }
        }
        if (start >= 0) raw += SpeechRegion(start * FRAME_MS.toLong(), frameDbs.size * FRAME_MS.toLong())

        // Bridge short gaps, drop blips, pad the edges.
        val merged = mutableListOf<SpeechRegion>()
        for (region in raw) {
            val last = merged.lastOrNull()
            if (last != null && region.startMs - last.endMs <= HANGOVER_MS) {
                merged[merged.lastIndex] = SpeechRegion(last.startMs, region.endMs)
            } else {
                merged += region
            }
        }
        return merged
            .filter { it.durationMs >= MIN_REGION_MS }
            .map { SpeechRegion((it.startMs - PAD_MS).coerceAtLeast(0), (it.endMs + PAD_MS).coerceAtMost(durationMs)) }
            .fold(mutableListOf()) { acc, region ->
                val last = acc.lastOrNull()
                if (last != null && region.startMs <= last.endMs) acc[acc.lastIndex] = SpeechRegion(last.startMs, region.endMs) else acc += region
                acc
            }
    }
}
