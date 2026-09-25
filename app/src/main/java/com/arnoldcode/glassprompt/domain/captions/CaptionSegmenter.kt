package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionWord

/**
 * Groups timed words into readable captions: at most two lines of [maxWordsPerLine] words,
 * breaking early after sentence punctuation, on long pauses, or when a caption would stay
 * on screen too long.
 */
object CaptionSegmenter {

    const val MAX_CAPTION_MS = 4_000L
    const val PAUSE_BREAK_MS = 700L
    const val MIN_CAPTION_MS = 600L

    private val SentenceEnd = Regex("[.!?…:;]$")
    private val ClauseEnd = Regex("[,]$")

    fun segment(words: List<CaptionWord>, maxWordsPerLine: Int, idOf: (Int) -> String): List<Caption> {
        val perCaption = (maxWordsPerLine.coerceAtLeast(1)) * 2
        val groups = mutableListOf<MutableList<CaptionWord>>()
        var current = mutableListOf<CaptionWord>()
        for (word in words.filter { it.text.isNotBlank() }) {
            val previous = current.lastOrNull()
            val breakBefore = previous != null && (
                current.size >= perCaption ||
                    word.startMs - previous.endMs >= PAUSE_BREAK_MS ||
                    word.endMs - current.first().startMs > MAX_CAPTION_MS ||
                    SentenceEnd.containsMatchIn(previous.text) ||
                    // A comma is a good break once the caption already holds a full line.
                    (ClauseEnd.containsMatchIn(previous.text) && current.size >= maxWordsPerLine)
                )
            if (breakBefore) {
                groups += current
                current = mutableListOf()
            }
            current += word
        }
        if (current.isNotEmpty()) groups += current

        return groups.mapIndexed { index, group ->
            val start = group.first().startMs
            // Linger a little after the last word, without overlapping the next caption.
            val nextStart = groups.getOrNull(index + 1)?.first()?.startMs ?: Long.MAX_VALUE
            val end = maxOf(group.last().endMs, start + MIN_CAPTION_MS).coerceAtMost(nextStart)
            Caption(
                id = idOf(index),
                text = lines(group.map { it.text }, maxWordsPerLine),
                startMs = start,
                endMs = end,
                words = group,
            )
        }
    }

    /** Joins words into up to two balanced lines. */
    fun lines(words: List<String>, maxWordsPerLine: Int): String {
        if (words.size <= maxWordsPerLine) return words.joinToString(" ")
        val split = (words.size + 1) / 2
        return words.take(split).joinToString(" ") + "\n" + words.drop(split).joinToString(" ")
    }
}
