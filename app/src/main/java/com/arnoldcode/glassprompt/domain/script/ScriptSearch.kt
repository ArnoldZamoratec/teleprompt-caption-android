package com.arnoldcode.glassprompt.domain.script

/** Literal (non-regex), case-insensitive find & replace used by the script editor. */
object ScriptSearch {

    /** Non-overlapping matches of [query] in [text], in order. Empty query → no matches. */
    fun findAll(text: String, query: String, ignoreCase: Boolean = true): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<IntRange>()
        var from = 0
        while (from <= text.length - query.length) {
            val index = text.indexOf(query, from, ignoreCase)
            if (index < 0) break
            matches += index until index + query.length
            from = index + query.length
        }
        return matches
    }

    /** Replaces the single match at [range]. */
    fun replaceAt(text: String, range: IntRange, replacement: String): String =
        text.replaceRange(range, replacement)

    /** Replaces every match; returns the new text and how many replacements were made. */
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean = true): Pair<String, Int> {
        val matches = findAll(text, query, ignoreCase)
        if (matches.isEmpty()) return text to 0
        val result = StringBuilder(text.length)
        var last = 0
        for (range in matches) {
            result.append(text, last, range.first).append(replacement)
            last = range.last + 1
        }
        result.append(text, last, text.length)
        return result.toString() to matches.size
    }
}
