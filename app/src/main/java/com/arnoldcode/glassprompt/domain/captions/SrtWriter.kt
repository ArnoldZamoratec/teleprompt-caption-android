package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import java.util.Locale

/** SubRip (.srt) output, the subtitle format YouTube and most editors accept. */
object SrtWriter {

    fun write(captions: List<Caption>): String = buildString {
        captions.sortedBy { it.startMs }.filter { it.text.isNotBlank() }.forEachIndexed { index, caption ->
            append(index + 1).append('\n')
            append(timestamp(caption.startMs)).append(" --> ").append(timestamp(caption.endMs)).append('\n')
            append(caption.text.trim()).append("\n\n")
        }
    }

    /** `HH:MM:SS,mmm` */
    fun timestamp(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val h = t / 3_600_000
        val m = t % 3_600_000 / 60_000
        val s = t % 60_000 / 1_000
        val millis = t % 1_000
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }
}
