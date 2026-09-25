package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionWord

/**
 * Pure caption-track edits. Every operation returns a new, time-ordered list that keeps the
 * invariants: no overlaps, each caption lasts at least [MIN_DURATION_MS], times within [0, duration].
 */
object CaptionEdits {

    const val MIN_DURATION_MS = 200L

    /** Replaces the text; word timings are re-spread over the caption proportionally to word length. */
    fun editText(captions: List<Caption>, id: String, text: String): List<Caption> = captions.map { caption ->
        if (caption.id != id) caption else caption.copy(text = text.trim(), words = spreadWords(text, caption.startMs, caption.endMs))
    }

    /** Moves a caption's start/end, clamped between its neighbours and the video bounds. */
    fun retime(captions: List<Caption>, id: String, startMs: Long, endMs: Long, videoDurationMs: Long): List<Caption> {
        val sorted = captions.sortedBy { it.startMs }
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0) return sorted
        val lower = sorted.getOrNull(index - 1)?.endMs ?: 0L
        val upper = sorted.getOrNull(index + 1)?.startMs ?: videoDurationMs
        if (upper - lower < MIN_DURATION_MS) return sorted
        val start = startMs.coerceIn(lower, upper - MIN_DURATION_MS)
        val end = endMs.coerceIn(start + MIN_DURATION_MS, upper)
        val caption = sorted[index]
        val retimed = caption.copy(startMs = start, endMs = end, words = rescale(caption.words, caption.startMs, caption.endMs, start, end))
        return sorted.toMutableList().also { it[index] = retimed }
    }

    /**
     * Splits a caption into two. With word timings the split happens between words at
     * [wordIndex] (words before it stay in the first half); otherwise at the time midpoint.
     */
    fun split(captions: List<Caption>, id: String, wordIndex: Int? = null, newId: String): List<Caption> {
        val caption = captions.firstOrNull { it.id == id } ?: return captions
        if (caption.durationMs < MIN_DURATION_MS * 2) return captions
        val tokens = caption.text.split(Whitespace).filter { it.isNotBlank() }
        if (tokens.size < 2) return captions

        val splitAt = (wordIndex ?: (tokens.size / 2)).coerceIn(1, tokens.size - 1)
        val timeAt = caption.words.takeIf { it.size == tokens.size }?.get(splitAt)?.startMs
            ?: (caption.startMs + caption.durationMs * splitAt / tokens.size)
        val splitTime = timeAt.coerceIn(caption.startMs + MIN_DURATION_MS, caption.endMs - MIN_DURATION_MS)

        val firstText = tokens.take(splitAt).joinToString(" ")
        val secondText = tokens.drop(splitAt).joinToString(" ")
        val (firstWords, secondWords) = if (caption.words.size == tokens.size) {
            caption.words.take(splitAt) to caption.words.drop(splitAt)
        } else {
            spreadWords(firstText, caption.startMs, splitTime) to spreadWords(secondText, splitTime, caption.endMs)
        }
        val first = caption.copy(text = firstText, endMs = splitTime, words = firstWords)
        val second = Caption(newId, secondText, splitTime, caption.endMs, secondWords)
        return captions.flatMap { if (it.id == id) listOf(first, second) else listOf(it) }.sortedBy { it.startMs }
    }

    /** Merges a caption with the one right after it. */
    fun mergeWithNext(captions: List<Caption>, id: String): List<Caption> {
        val sorted = captions.sortedBy { it.startMs }
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0 || index == sorted.lastIndex) return sorted
        val a = sorted[index]
        val b = sorted[index + 1]
        val merged = a.copy(
            text = (a.text + " " + b.text).replace(Whitespace, " ").trim(),
            endMs = b.endMs,
            words = a.words + b.words,
        )
        return sorted.filterIndexed { i, _ -> i != index + 1 }.toMutableList().also { it[index] = merged }
    }

    fun delete(captions: List<Caption>, id: String): List<Caption> = captions.filterNot { it.id == id }

    /**
     * Inserts a new caption at [atMs] lasting up to [lengthMs], fitted into the gap it falls in.
     * Returns the list unchanged if there is no room.
     */
    fun insert(captions: List<Caption>, atMs: Long, text: String, newId: String, videoDurationMs: Long, lengthMs: Long = 1_500): List<Caption> {
        val sorted = captions.sortedBy { it.startMs }
        if (sorted.any { atMs in it.startMs until it.endMs }) return sorted
        val gapEnd = sorted.firstOrNull { it.startMs >= atMs }?.startMs ?: videoDurationMs
        val start = atMs.coerceAtLeast(0)
        val end = minOf(start + lengthMs, gapEnd)
        if (end - start < MIN_DURATION_MS) return sorted
        return (sorted + Caption(newId, text, start, end, spreadWords(text, start, end))).sortedBy { it.startMs }
    }

    /** Estimated word timings: each word gets time proportional to its length (+1 for the gap). */
    fun spreadWords(text: String, startMs: Long, endMs: Long): List<CaptionWord> {
        val tokens = text.split(Whitespace).filter { it.isNotBlank() }
        if (tokens.isEmpty() || endMs <= startMs) return emptyList()
        val weights = tokens.map { it.length + 1 }
        val total = weights.sum().toDouble()
        var cursor = startMs.toDouble()
        return tokens.mapIndexed { i, token ->
            val duration = (endMs - startMs) * weights[i] / total
            val word = CaptionWord(token, cursor.toLong(), if (i == tokens.lastIndex) endMs else (cursor + duration).toLong())
            cursor += duration
            word
        }
    }

    private fun rescale(words: List<CaptionWord>, oldStart: Long, oldEnd: Long, newStart: Long, newEnd: Long): List<CaptionWord> {
        val oldSpan = (oldEnd - oldStart).coerceAtLeast(1).toDouble()
        val newSpan = (newEnd - newStart).toDouble()
        fun map(t: Long) = (newStart + (t - oldStart) / oldSpan * newSpan).toLong().coerceIn(newStart, newEnd)
        return words.map { it.copy(startMs = map(it.startMs), endMs = map(it.endMs)) }
    }

    private val Whitespace = Regex("\\s+")
}
