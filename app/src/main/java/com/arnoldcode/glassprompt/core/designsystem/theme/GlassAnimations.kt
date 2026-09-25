package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Motion tokens: short, natural, never showy. */
@Immutable
data class GlassAnimations(
    val durationShort: Int = 120,
    val durationMedium: Int = 220,
    val durationLong: Int = 320,
    val pressedScale: Float = 0.97f,
) {
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(durationMedium, easing = EmphasizedDecelerate)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationShort, easing = EmphasizedAccelerate)

    companion object {
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    }
}

val LocalGlassAnimations = staticCompositionLocalOf { GlassAnimations() }
