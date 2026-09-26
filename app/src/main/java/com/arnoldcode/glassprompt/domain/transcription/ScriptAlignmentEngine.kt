package com.arnoldcode.glassprompt.domain.transcription

import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.arnoldcode.glassprompt.domain.model.Transcript
import javax.inject.Inject

/**
 * Offline fallback that always works: the user read a known script, so its words are laid
 * over the detected speech in order, each taking time proportional to its length. Word
 * boundaries never straddle a pause — a word that would cross a gap moves to the region
 * holding most of it.
 */
class ScriptAlignmentEngine @Inject constructor() : TranscriptionEngine {

    override val id: String = ID

    override suspend fun isAvailable(language: String): Boolean = true

    override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Float) -> Unit): Transcript {
        val words = align(request.scriptText, request.regions.ifEmpty { listOf(SpeechRegion(0, request.durationMs)) })
        onProgress(1f)
        return Transcript(words = words, language = request.language, engineId = id, hasWordTimings = false)
    }

    companion object {
        const val ID = "script-alignment"

        private val Whitespace = Regex("\\s+")

        fun align(text: String, regions: List<SpeechRegion>): List<CaptionWord> {
            val tokens = text.split(Whitespace).filter { token -> token.any(Char::isLetterOrDigit) }
            val speech = regions.filter { it.durationMs > 0 }.sortedBy { it.startMs }
            if (tokens.isEmpty() || speech.isEmpty()) return emptyList()

            val totalSpeech = speech.sumOf { it.durationMs }.toDouble()
            val weights = tokens.map { it.length + 1.0 }
            val totalWeight = weights.sum()

            // Virtual timeline = speech only, gaps removed. Words advance monotonically, so the region
            // cursor only moves forward: O(words + regions) instead of rescanning per word.
            val regionStarts = DoubleArray(speech.size)
            var acc = 0.0
            speech.forEachIndexed { i, region ->
                regionStarts[i] = acc
                acc += region.durationMs
            }
            var cursor = 0.0
            var regionIndex = 0
            val result = ArrayList<CaptionWord>(tokens.size)
            tokens.forEachIndexed { i, token ->
                val length = totalSpeech * weights[i] / totalWeight
                while (regionIndex < speech.lastIndex && cursor >= regionStarts[regionIndex] + speech[regionIndex].durationMs) regionIndex++
                val (start, end) = toReal(cursor, cursor + length, speech, regionStarts, regionIndex)
                result += CaptionWord(token, start, end)
                cursor += length
            }
            return result
        }

        /** Maps a virtual [vStart, vEnd) span to real time, keeping it inside region [index] or the next one. */
        private fun toReal(vStart: Double, vEnd: Double, regions: List<SpeechRegion>, starts: DoubleArray, index: Int): Pair<Long, Long> {
            val region = regions[index]
            val offset = starts[index]
            val regionEnd = offset + region.durationMs
            val isLast = index == regions.lastIndex
            val inThis = minOf(vEnd, regionEnd) - vStart
            val inNext = vEnd - regionEnd
            return if (inNext > inThis && !isLast) {
                // Most of the word is after the pause: it starts the next region, keeping only
                // its share there so it can't overlap the word that follows.
                val next = regions[index + 1]
                next.startMs to (next.startMs + inNext.toLong()).coerceAtMost(next.endMs)
            } else {
                val start = (region.startMs + (vStart - offset).toLong()).coerceAtMost(region.endMs)
                start to (region.startMs + (minOf(vEnd, regionEnd) - offset).toLong()).coerceAtMost(region.endMs)
            }
        }
    }
}
