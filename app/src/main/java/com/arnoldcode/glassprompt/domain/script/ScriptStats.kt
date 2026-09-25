package com.arnoldcode.glassprompt.domain.script

import kotlin.math.roundToLong

data class ScriptStats(
    val words: Int,
    val characters: Int,
    val charactersNoSpaces: Int,
    val paragraphs: Int,
    val readingTimeSeconds: Long,
) {
    companion object {
        val Empty = ScriptStats(0, 0, 0, 0, 0)

        /** Natural reading pace for spoken video, used at teleprompter speed 1.0. */
        const val WORDS_PER_MINUTE = 150

        private val Paragraphs = Regex("\\n\\s*\\n")

        /** Stats for [text] read at teleprompter [speed] (1.0 = [WORDS_PER_MINUTE]). */
        fun of(text: String, speed: Float = 1f): ScriptStats {
            val words = ScriptText.countWords(text)
            val effectiveWpm = WORDS_PER_MINUTE * speed.coerceAtLeast(0.1f)
            return ScriptStats(
                words = words,
                characters = text.length,
                charactersNoSpaces = text.count { !it.isWhitespace() },
                paragraphs = text.split(Paragraphs).count { it.isNotBlank() },
                readingTimeSeconds = (words / effectiveWpm * 60).roundToLong(),
            )
        }
    }
}
