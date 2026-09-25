package com.arnoldcode.glassprompt.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Paints search matches behind the text without changing it (identity offset mapping).
 * Ranges past the end are skipped: matches can lag one frame behind fast typing.
 */
internal class MatchHighlightTransformation(
    private val matches: List<IntRange>,
    private val current: Int,
    private val matchColor: Color,
    private val currentColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val length = text.length
        val styled = AnnotatedString.Builder(text).apply {
            matches.forEachIndexed { index, range ->
                if (range.last < length) {
                    addStyle(SpanStyle(background = if (index == current) currentColor else matchColor), range.first, range.last + 1)
                }
            }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is MatchHighlightTransformation && other.matches == matches && other.current == current &&
            other.matchColor == matchColor && other.currentColor == currentColor

    override fun hashCode(): Int = ((matches.hashCode() * 31 + current) * 31 + matchColor.hashCode()) * 31 + currentColor.hashCode()
}
