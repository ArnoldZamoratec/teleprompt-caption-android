package com.arnoldcode.glassprompt.core.designsystem.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Soft blue/violet light blobs on the deep background: the "scene" that glass refracts.
 *
 * Static by default, because animating it would re-run every blur on screen each frame.
 * Pass [animated] = true only on light screens such as onboarding.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    val colors = GlassTheme.colors
    val phase = auroraPhase(animated && !GlassTheme.reduceEffects)
    Canvas(modifier) {
        drawRect(colors.background)
        val t = phase.value
        drawBlob(colors.auroraBlue, center = Offset(0.15f + 0.05f * sin(t), 0.12f + 0.03f * cos(t)), radius = 0.75f, alpha = 0.34f)
        drawBlob(colors.auroraViolet, center = Offset(0.95f - 0.05f * cos(t), 0.38f + 0.04f * sin(t)), radius = 0.65f, alpha = 0.28f)
        drawBlob(colors.auroraTeal, center = Offset(0.25f + 0.04f * cos(t), 0.92f - 0.03f * sin(t)), radius = 0.6f, alpha = 0.16f)
    }
}

@Composable
private fun auroraPhase(animated: Boolean): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "aurora")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraPhase",
    )
}

/** [center] and [radius] are fractions of the canvas (radius relative to the larger side). */
private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float, alpha: Float) {
    val c = Offset(center.x * size.width, center.y * size.height)
    val r = radius * maxOf(size.width, size.height)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
}
