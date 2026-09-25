package com.arnoldcode.glassprompt.domain.model

import kotlinx.serialization.Serializable

/** A timed word. Times are milliseconds from the start of the take. */
@Serializable
data class CaptionWord(val text: String, val startMs: Long, val endMs: Long)

/** One on-screen caption (1–2 lines). [words] drive word highlight / karaoke animations. */
data class Caption(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<CaptionWord> = emptyList(),
) {
    val durationMs: Long get() = endMs - startMs

    fun isVisibleAt(timeMs: Long): Boolean = timeMs in startMs until endMs
}

enum class CaptionPreset { CLASSIC, BOLD, MINIMAL, CREATOR, NEON, GLASS, KARAOKE, CUSTOM }

enum class CaptionAnimation { NONE, FADE, POP, SLIDE, SCALE, WORD_HIGHLIGHT, KARAOKE }

enum class CaptionPosition { TOP, CENTER, BOTTOM }

/**
 * Visual style of a take's captions. Colors are ARGB ints; sizes are relative to the frame's
 * shorter side ([textSizeFraction] = text size / min(width, height)), so a small preview, a 4K
 * export, portrait and landscape all look the same.
 */
@Serializable
data class CaptionStyle(
    val preset: CaptionPreset = CaptionPreset.CLASSIC,
    val textSizeFraction: Float = 0.056f,
    val bold: Boolean = true,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val highlightColor: Int = 0xFFFFD60A.toInt(),
    val backgroundColor: Int = 0xFF000000.toInt(),
    val backgroundOpacity: Float = 0.55f,
    val strokeColor: Int = 0xFF000000.toInt(),
    /** Stroke width relative to the text size (0 = none). */
    val strokeWidth: Float = 0f,
    val shadow: Boolean = false,
    val position: CaptionPosition = CaptionPosition.BOTTOM,
    val animation: CaptionAnimation = CaptionAnimation.FADE,
    val maxWordsPerLine: Int = 5,
    val uppercase: Boolean = false,
) {
    companion object {
        val SizeRange = 0.03f..0.11f
        val WordsPerLineRange = 1..8
    }
}

/** Built-in looks. Editing any value turns the style into [CaptionPreset.CUSTOM]. */
object CaptionStyles {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    val presets: Map<CaptionPreset, CaptionStyle> = mapOf(
        CaptionPreset.CLASSIC to CaptionStyle(),
        CaptionPreset.BOLD to CaptionStyle(
            preset = CaptionPreset.BOLD, textSizeFraction = 0.075f, backgroundOpacity = 0f,
            strokeWidth = 0.14f, shadow = true, position = CaptionPosition.CENTER,
            animation = CaptionAnimation.POP, maxWordsPerLine = 3, uppercase = true,
        ),
        CaptionPreset.MINIMAL to CaptionStyle(
            preset = CaptionPreset.MINIMAL, textSizeFraction = 0.048f, bold = false, backgroundOpacity = 0f,
            shadow = true, animation = CaptionAnimation.FADE, maxWordsPerLine = 7,
        ),
        CaptionPreset.CREATOR to CaptionStyle(
            preset = CaptionPreset.CREATOR, textSizeFraction = 0.07f, highlightColor = 0xFF5B8CFF.toInt(),
            backgroundOpacity = 0f, strokeWidth = 0.12f, shadow = true, position = CaptionPosition.CENTER,
            animation = CaptionAnimation.WORD_HIGHLIGHT, maxWordsPerLine = 3, uppercase = true,
        ),
        CaptionPreset.NEON to CaptionStyle(
            preset = CaptionPreset.NEON, textSizeFraction = 0.065f, textColor = 0xFF7DF9FF.toInt(),
            highlightColor = 0xFFFF4DD8.toInt(), backgroundOpacity = 0f, strokeColor = 0xFF1B0B3A.toInt(),
            strokeWidth = 0.08f, shadow = true, animation = CaptionAnimation.SCALE, maxWordsPerLine = 4,
        ),
        CaptionPreset.GLASS to CaptionStyle(
            preset = CaptionPreset.GLASS, textSizeFraction = 0.056f, backgroundColor = WHITE,
            backgroundOpacity = 0.22f, strokeWidth = 0f, shadow = true, animation = CaptionAnimation.SLIDE,
            maxWordsPerLine = 5,
        ),
        CaptionPreset.KARAOKE to CaptionStyle(
            preset = CaptionPreset.KARAOKE, textSizeFraction = 0.063f, textColor = WHITE,
            highlightColor = 0xFF3DD68C.toInt(), backgroundColor = BLACK, backgroundOpacity = 0.45f,
            strokeWidth = 0.06f, animation = CaptionAnimation.KARAOKE, maxWordsPerLine = 4,
        ),
    )

    fun of(preset: CaptionPreset): CaptionStyle = presets[preset] ?: CaptionStyle()
}

/** Result of speech-to-text (or script alignment) for a take. */
data class Transcript(
    val words: List<CaptionWord>,
    val language: String,
    val engineId: String,
    /** True when [words] carry real per-word timestamps (not estimated). */
    val hasWordTimings: Boolean,
)

/** A take's caption track: captions + the style they are drawn with. */
data class CaptionTrack(
    val takeId: String,
    val captions: List<Caption>,
    val style: CaptionStyle,
    val language: String,
    val engineId: String,
)

/** Progress of the background transcription of a take. */
sealed interface TranscriptionState {
    data object Idle : TranscriptionState
    data class Running(val stage: Stage, val progress: Float) : TranscriptionState
    data object Done : TranscriptionState
    data class Failed(val reason: Reason) : TranscriptionState

    enum class Stage { EXTRACTING_AUDIO, DETECTING_SPEECH, TRANSCRIBING, SAVING }
    enum class Reason { NO_AUDIO, NO_SPEECH, ENGINE_ERROR, NOT_FOUND }
}

/** How captions are produced. */
enum class TranscriptionMode {
    /** On-device speech recognition when available, otherwise align the script. */
    AUTO,

    /** Always use the script text, timed to the detected speech. Exact spelling, fully offline. */
    SCRIPT,
}
