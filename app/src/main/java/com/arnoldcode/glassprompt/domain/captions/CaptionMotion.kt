package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionAnimation
import kotlin.math.min
import kotlin.math.pow

/** How a caption looks at one instant. Offsets are in multiples of the text size. */
data class CaptionFrame(
    val alpha: Float,
    val scale: Float,
    val offsetY: Float,
    /** Word drawn in the highlight color, or -1. */
    val highlightWord: Int,
    /** Words 0..this are drawn in the highlight color (karaoke), or -1. */
    val filledThrough: Int,
)

/**
 * Animations as pure functions of time, shared by the live preview and the export, so both
 * draw exactly the same frame for the same timestamp.
 */
object CaptionMotion {

    const val ENTER_MS = 220L
    const val EXIT_MS = 140L

    /** The caption on screen at [timeMs] (captions are time-ordered and never overlap). */
    fun captionAt(captions: List<Caption>, timeMs: Long): Caption? = captions.getOrNull(indexAt(captions, timeMs))

    /** Index of the caption on screen at [timeMs], or -1. O(log n): called on every frame. */
    fun indexAt(captions: List<Caption>, timeMs: Long): Int {
        var low = 0
        var high = captions.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val caption = captions[mid]
            when {
                timeMs < caption.startMs -> high = mid - 1
                timeMs >= caption.endMs -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    /** Index of the word being spoken at [timeMs]: the last one that has started, -1 before the first. */
    fun activeWord(caption: Caption, timeMs: Long): Int = caption.words.indexOfLast { it.startMs <= timeMs }

    fun frame(caption: Caption, animation: CaptionAnimation, timeMs: Long): CaptionFrame {
        val enter = ((timeMs - caption.startMs).toFloat() / ENTER_MS).coerceIn(0f, 1f)
        val exit = ((caption.endMs - timeMs).toFloat() / EXIT_MS).coerceIn(0f, 1f)
        val fade = min(easeOut(enter), exit)
        val word = activeWord(caption, timeMs)
        return when (animation) {
            CaptionAnimation.NONE -> CaptionFrame(1f, 1f, 0f, -1, -1)
            CaptionAnimation.FADE -> CaptionFrame(fade, 1f, 0f, -1, -1)
            CaptionAnimation.POP -> CaptionFrame(min((enter * 3).coerceAtMost(1f), exit), 0.6f + 0.4f * backOut(enter), 0f, -1, -1)
            CaptionAnimation.SLIDE -> CaptionFrame(fade, 1f, 0.6f * (1f - easeOut(enter)), -1, -1)
            CaptionAnimation.SCALE -> CaptionFrame(fade, 0.85f + 0.15f * easeOut(enter), 0f, -1, -1)
            CaptionAnimation.WORD_HIGHLIGHT -> CaptionFrame(fade, 1f, 0f, word, -1)
            CaptionAnimation.KARAOKE -> CaptionFrame(fade, 1f, 0f, -1, word)
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

    /** Overshoots to ~1.1 then settles at 1. */
    private fun backOut(t: Float): Float {
        val c = 1.70158f
        val x = t - 1f
        return 1f + (c + 1f) * x * x * x + c * x * x
    }
}
