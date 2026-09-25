package com.arnoldcode.glassprompt.core.captions

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.withTranslation
import com.arnoldcode.glassprompt.domain.captions.CaptionFrame
import com.arnoldcode.glassprompt.domain.captions.CaptionMotion
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionPosition
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a caption on an [android.graphics.Canvas] for a given time. The single renderer behind
 * the editor preview (Compose `nativeCanvas`) and the exported video (Media3 bitmap overlay):
 * every size is derived from the frame, so a 360 px preview and a 4K export match.
 *
 * Not thread-safe: use one instance per drawing surface.
 */
class CaptionRenderer {

    private val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var cached: Cached? = null

    /** Draws whichever caption of [captions] is on screen at [timeMs]; nothing between captions. */
    fun draw(canvas: Canvas, width: Float, height: Float, captions: List<Caption>, style: CaptionStyle, timeMs: Long) {
        val caption = CaptionMotion.captionAt(captions, timeMs) ?: return
        drawCaption(canvas, width, height, caption, style, CaptionMotion.frame(caption, style.animation, timeMs))
    }

    fun drawCaption(canvas: Canvas, width: Float, height: Float, caption: Caption, style: CaptionStyle, frame: CaptionFrame) {
        if (frame.alpha <= 0f || width <= 0f || height <= 0f || caption.text.isBlank()) return
        val textSize = style.textSizeFraction * min(width, height)
        val maxWidth = (width * MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
        val layouts = layoutsFor(caption, style, textSize, maxWidth, frame)

        val textHeight = layouts.fill.height.toFloat()
        val textWidth = (0 until layouts.fill.lineCount).maxOf { layouts.fill.getLineWidth(it) }
        val padH = textSize * 0.45f
        val padV = textSize * 0.22f
        val centerY = when (style.position) {
            CaptionPosition.TOP -> height * EDGE_MARGIN + padV + textHeight / 2
            CaptionPosition.CENTER -> height / 2
            CaptionPosition.BOTTOM -> height * (1 - EDGE_MARGIN) - padV - textHeight / 2
        } + frame.offsetY * textSize

        // withTranslation restores to its checkpoint, which also pops the alpha layer below.
        canvas.withTranslation(width / 2, centerY) {
            scale(frame.scale, frame.scale)
            rect.set(-textWidth / 2 - padH, -textHeight / 2 - padV, textWidth / 2 + padH, textHeight / 2 + padV)
            if (frame.alpha < 1f) {
                // One layer for everything, so overlapping stroke, fill and box fade together.
                saveLayerAlpha(rect.left - textSize, rect.top - textSize, rect.right + textSize, rect.bottom + textSize, (frame.alpha * 255).roundToInt())
            }
            if (style.backgroundOpacity > 0f) {
                backgroundPaint.color = withAlpha(style.backgroundColor, style.backgroundOpacity)
                val radius = textSize * 0.32f
                drawRoundRect(rect, radius, radius, backgroundPaint)
            }
            translate(-maxWidth / 2f, -textHeight / 2)
            layouts.stroke?.draw(this)
            layouts.fill.draw(this)
        }
    }

    private fun layoutsFor(caption: Caption, style: CaptionStyle, textSize: Float, maxWidth: Int, frame: CaptionFrame): Cached {
        val key = Key(caption.id, caption.text, caption.words.size, style, textSize, maxWidth, frame.highlightWord, frame.filledThrough)
        cached?.takeIf { it.key == key }?.let { return it }

        val text = if (style.uppercase) caption.text.uppercase(Locale.getDefault()) else caption.text
        val typeface = typeface(style.bold)

        fillPaint.set(TextPaint(Paint.ANTI_ALIAS_FLAG))
        fillPaint.typeface = typeface
        fillPaint.textSize = textSize
        fillPaint.color = style.textColor
        if (style.shadow) fillPaint.setShadowLayer(textSize * 0.14f, 0f, textSize * 0.05f, SHADOW) else fillPaint.clearShadowLayer()

        val styled = SpannableString(text)
        val highlighted = highlightedRanges(text, caption.words.size, frame)
        highlighted.forEach { range ->
            styled.setSpan(ForegroundColorSpan(style.highlightColor), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val fill = layout(styled, fillPaint, maxWidth)

        val stroke = if (style.strokeWidth > 0f) {
            strokePaint.typeface = typeface
            strokePaint.textSize = textSize
            strokePaint.color = style.strokeColor
            strokePaint.strokeWidth = style.strokeWidth * textSize
            layout(text, strokePaint, maxWidth)
        } else {
            null
        }
        return Cached(key, fill, stroke).also { cached = it }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.04f)
            .setIncludePad(false)
            .build()

    private fun typeface(bold: Boolean): Typeface = when {
        !bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(Typeface.SANS_SERIF, 800, false)
        else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private data class Key(
        val id: String,
        val text: String,
        val wordCount: Int,
        val style: CaptionStyle,
        val textSize: Float,
        val maxWidth: Int,
        val highlightWord: Int,
        val filledThrough: Int,
    )

    private class Cached(val key: Key, val fill: StaticLayout, val stroke: StaticLayout?)

    companion object {
        private const val MAX_WIDTH_FRACTION = 0.86f
        private const val EDGE_MARGIN = 0.1f
        private const val SHADOW = 0xAA000000.toInt()

        private val Word = Regex("\\S+")

        /**
         * Character ranges to paint with the highlight color. Timed words map to on-screen words
         * by position; if an edit made the counts differ, proportionally.
         */
        internal fun highlightedRanges(text: String, wordCount: Int, frame: CaptionFrame): List<IntRange> {
            if (wordCount <= 0 || (frame.highlightWord < 0 && frame.filledThrough < 0)) return emptyList()
            val ranges = Word.findAll(text).map { it.range }.toList()
            if (ranges.isEmpty()) return emptyList()
            fun toVisible(index: Int) = if (ranges.size == wordCount) index else index * ranges.size / wordCount
            return when {
                frame.highlightWord >= 0 -> listOf(ranges[toVisible(frame.highlightWord).coerceAtMost(ranges.lastIndex)])
                else -> ranges.take(toVisible(frame.filledThrough).coerceAtMost(ranges.lastIndex) + 1)
            }
        }

        private fun withAlpha(color: Int, opacity: Float): Int =
            Color.argb((Color.alpha(color) * opacity).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
