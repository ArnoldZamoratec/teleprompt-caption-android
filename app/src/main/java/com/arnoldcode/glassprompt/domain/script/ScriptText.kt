package com.arnoldcode.glassprompt.domain.script

import java.text.Normalizer

/** Pure text helpers shared by the editor, the stats and project search. */
object ScriptText {

    /** Longest script accepted from an import (about 30k words, far beyond any teleprompter read). */
    const val MAX_IMPORT_CHARS = 200_000

    /** U+FEFF written by some editors at the start of UTF-8 files. */
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()
    private val Whitespace = Regex("\\s+")
    private val Diacritics = Regex("\\p{Mn}+")

    /** A token counts as a word when it contains at least one letter or digit ("—" or "..." do not). */
    fun countWords(text: String): Int =
        text.split(Whitespace).count { token -> token.any(Char::isLetterOrDigit) }

    /** Unifies line endings, drops a UTF-8 BOM and trailing whitespace. */
    fun normalize(raw: String): String =
        raw.removePrefix(BYTE_ORDER_MARK)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()

    /** Appends [addition] after [current], separated by a blank line. */
    fun append(current: String, addition: String): String = when {
        current.isBlank() -> addition
        addition.isBlank() -> current
        else -> current.trimEnd() + "\n\n" + addition
    }

    /** Case- and accent-insensitive form used for search ("canción" matches "CANCION"). */
    fun searchKey(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Diacritics, "").lowercase()
}
