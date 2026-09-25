package com.arnoldcode.glassprompt.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassColors
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassDepth
import com.arnoldcode.glassprompt.core.designsystem.theme.LocalGlassColors
import com.arnoldcode.glassprompt.core.designsystem.theme.LocalGlassElevation
import com.arnoldcode.glassprompt.core.designsystem.theme.LocalReduceEffects
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Frosted glass: soft shadow → backdrop blur (or translucent tint fallback) → tint →
 * specular sheen → luminous rim. Use for cards, panels, bars and dialogs.
 *
 * @param tint optional colour wash over the glass (e.g. accent for primary actions).
 * @param depth defaults to the theme's `raised` depth.
 */
fun Modifier.glassSurface(
    shape: Shape,
    depth: GlassDepth? = null,
    tint: Color? = null,
): Modifier = composed {
    val colors = LocalGlassColors.current
    val resolvedDepth = depth ?: LocalGlassElevation.current.raised
    val hazeState = LocalGlassHazeState.current
    val useBlur = hazeState != null && !LocalReduceEffects.current
    val fill = colors.glassFill.boost(resolvedDepth.fillAlphaBoost).let { base ->
        if (tint != null) tint.compositeOver(base) else base
    }

    this
        .glassShadow(shape, resolvedDepth, colors)
        .clip(shape)
        .then(
            if (useBlur) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeBlurStyle {
                        blurRadius(resolvedDepth.blurRadius)
                        backgroundColor(colors.background)
                        colorEffects(listOf(HazeColorEffect.tint(fill)))
                        noiseFactor(0.04f)
                    },
                )
            } else {
                Modifier.background(fallbackFill(colors, fill), shape)
            },
        )
        .glassHighlights(shape, colors, resolvedDepth.rimAlpha)
}

/**
 * Refractive "liquid" glass for hero elements (record button, bottom bar). Bends and
 * brightens the backdrop at its edges and reacts to presses. Uses runtime shaders where
 * supported (Android 13+); the library falls back to a frosted look elsewhere.
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    tint: Color = Color.Transparent,
    interactionSource: InteractionSource? = null,
): Modifier = composed {
    val colors = LocalGlassColors.current
    val hazeState = LocalGlassHazeState.current
    val depth = LocalGlassElevation.current.floating
    if (hazeState == null || LocalReduceEffects.current) {
        return@composed this.glassSurface(shape, depth, tint.takeIf { it.alpha > 0f })
    }
    this
        .glassShadow(shape, depth, colors)
        .hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = GlassStyle.regular.then {
                shape(shape)
                tint(tint)
                edgeShadow(colors.glassShadow.copy(alpha = 0.18f))
            },
            interactionSource = interactionSource,
        )
        .glassHighlights(shape, colors, rimAlpha = 0.6f)
}

/**
 * Only the sheen and luminous rim, for solid surfaces (gradient buttons, chips) that should
 * read as part of the glass family without sampling the backdrop.
 */
fun Modifier.glassRim(shape: Shape, rimAlpha: Float = 1f): Modifier = composed {
    glassHighlights(shape, LocalGlassColors.current, rimAlpha)
}

private fun Modifier.glassShadow(shape: Shape, depth: GlassDepth, colors: GlassColors): Modifier =
    if (depth.shadowElevation.value <= 0f) {
        this
    } else {
        dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = depth.shadowElevation,
                color = colors.glassShadow,
                offset = DpOffset(0.dp, depth.shadowElevation / 3),
            ),
        )
    }

/** Specular sheen on the upper half plus a gradient rim that fakes a top-left light source. */
private fun Modifier.glassHighlights(shape: Shape, colors: GlassColors, rimAlpha: Float): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val sheen = Brush.verticalGradient(
            0f to colors.glassSpecular,
            0.45f to Color.Transparent,
            endY = size.height,
        )
        val rim = Brush.linearGradient(
            colors = listOf(
                colors.glassBorderHighlight.copy(alpha = colors.glassBorderHighlight.alpha * rimAlpha),
                colors.glassBorderShade,
                colors.glassBorderHighlight.copy(alpha = colors.glassBorderHighlight.alpha * rimAlpha * 0.4f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val stroke = Stroke(width = 1.dp.toPx())
        onDrawWithContent {
            clipPath(clip) { drawRect(sheen) }
            drawContent()
            drawOutline(outline, rim, style = stroke)
        }
    }

private fun Color.boost(amount: Float): Color = copy(alpha = (alpha + amount).coerceAtMost(1f))

/** Without blur the glass needs more body to stay legible over busy backgrounds. */
private fun fallbackFill(colors: GlassColors, fill: Color): Color =
    fill.compositeOver(colors.backgroundElevated.copy(alpha = if (colors.isDark) 0.72f else 0.6f))

