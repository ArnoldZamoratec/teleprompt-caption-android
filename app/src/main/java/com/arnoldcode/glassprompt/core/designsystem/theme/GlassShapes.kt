package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class GlassShapes(
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large: RoundedCornerShape = RoundedCornerShape(28.dp),
    val extraLarge: RoundedCornerShape = RoundedCornerShape(36.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

internal fun GlassShapes.toMaterialShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = small,
    medium = medium,
    large = large,
    extraLarge = extraLarge,
)

val LocalGlassShapes = staticCompositionLocalOf { GlassShapes() }
