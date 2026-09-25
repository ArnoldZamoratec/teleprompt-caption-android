package com.arnoldcode.glassprompt.domain.teleprompter

import com.arnoldcode.glassprompt.domain.script.ScriptStats

/**
 * Pure auto-scroll math. The scroll rate is derived from the laid-out script itself — total
 * scroll distance divided by the reading time at [ScriptStats.WORDS_PER_MINUTE] — so "1.0x"
 * reads at the same words per minute whatever the font size, spacing or screen.
 */
object ScrollEngine {

    /** Scroll speed in px/s to cover [scrollDistancePx] while reading [wordCount] words at [speed]. */
    fun pixelsPerSecond(scrollDistancePx: Float, wordCount: Int, speed: Float): Float {
        if (wordCount <= 0 || scrollDistancePx <= 0f) return 0f
        val wordsPerSecond = ScriptStats.WORDS_PER_MINUTE * speed.coerceAtLeast(MIN_SPEED) / 60f
        val readingSeconds = wordCount / wordsPerSecond
        return scrollDistancePx / readingSeconds
    }

    /** Distance to scroll for a frame of [frameNanos] at [pixelsPerSecond]. */
    fun delta(pixelsPerSecond: Float, frameNanos: Long): Float =
        if (frameNanos <= 0) 0f else pixelsPerSecond * (frameNanos.coerceAtMost(MAX_FRAME_NANOS) / 1_000_000_000f)

    /** Remaining reading time (seconds) from [offsetPx] to the end at the current rate. */
    fun remainingSeconds(offsetPx: Float, maxOffsetPx: Float, pixelsPerSecond: Float): Long =
        if (pixelsPerSecond <= 0f) 0 else ((maxOffsetPx - offsetPx).coerceAtLeast(0f) / pixelsPerSecond).toLong()

    /** Speed steps offered by the − / + buttons. */
    fun step(speed: Float, up: Boolean): Float {
        val next = if (up) speed + SPEED_STEP else speed - SPEED_STEP
        return ((next * 10).let(Math::round) / 10f).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 3f
    const val SPEED_STEP = 0.1f

    /** A dropped frame (app paused, GC) must not make the text jump. */
    private const val MAX_FRAME_NANOS = 100_000_000L
}
