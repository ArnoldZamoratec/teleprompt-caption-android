package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Root theme. Provides Glass tokens through composition locals and maps them onto
 * [MaterialTheme] so stock Material 3 components inherit the GlassPrompt identity.
 */
@Composable
fun GlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkGlassColors else LightGlassColors
    val shapes = GlassShapes()
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassShapes provides shapes,
        LocalGlassSpacing provides GlassSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = GlassTypography,
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}

/** Accessor mirroring `MaterialTheme.xxx`: `GlassTheme.colors.accent`. */
object GlassTheme {
    val colors: GlassColors
        @Composable @ReadOnlyComposable
        get() = LocalGlassColors.current

    val shapes: GlassShapes
        @Composable @ReadOnlyComposable
        get() = LocalGlassShapes.current

    val spacing: GlassSpacing
        @Composable @ReadOnlyComposable
        get() = LocalGlassSpacing.current
}

private fun GlassColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accentSecondary,
        onSecondary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = backgroundElevated,
        onSurface = textPrimary,
        surfaceVariant = glassFillStrong,
        onSurfaceVariant = textSecondary,
        outline = glassBorderHighlight,
        outlineVariant = glassBorderShade,
        error = error,
        onError = onAccent,
    )
}
