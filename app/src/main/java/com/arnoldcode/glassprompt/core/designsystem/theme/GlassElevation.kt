package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth of a glass layer. Higher levels are more opaque, cast a softer, wider shadow and
 * get a brighter rim so stacked panes read clearly against each other.
 */
@Immutable
data class GlassDepth(
    val fillAlphaBoost: Float,
    val shadowElevation: Dp,
    val rimAlpha: Float,
    val blurRadius: Dp,
)

@Immutable
data class GlassElevation(
    val flat: GlassDepth = GlassDepth(fillAlphaBoost = 0f, shadowElevation = 0.dp, rimAlpha = 0.7f, blurRadius = 16.dp),
    val raised: GlassDepth = GlassDepth(fillAlphaBoost = 0.03f, shadowElevation = 12.dp, rimAlpha = 0.9f, blurRadius = 24.dp),
    val floating: GlassDepth = GlassDepth(fillAlphaBoost = 0.06f, shadowElevation = 24.dp, rimAlpha = 1f, blurRadius = 32.dp),
)

val LocalGlassElevation = staticCompositionLocalOf { GlassElevation() }
