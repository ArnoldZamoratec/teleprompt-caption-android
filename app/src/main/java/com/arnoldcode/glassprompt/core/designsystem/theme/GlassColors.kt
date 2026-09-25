package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for the Liquid Glass design system.
 * Glass surfaces are translucent white (dark theme) or translucent white-on-grey (light theme);
 * accents are a restrained tech blue and a subtle violet.
 */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundElevated: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorderHighlight: Color,
    val glassBorderShade: Color,
    val glassSpecular: Color,
    val glassShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val recording: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val auroraBlue: Color,
    val auroraViolet: Color,
    val auroraTeal: Color,
)

internal val DarkGlassColors = GlassColors(
    isDark = true,
    background = Color(0xFF07080C),
    backgroundElevated = Color(0xFF0E1016),
    glassFill = Color.White.copy(alpha = 0.07f),
    glassFillStrong = Color.White.copy(alpha = 0.13f),
    glassBorderHighlight = Color.White.copy(alpha = 0.30f),
    glassBorderShade = Color.White.copy(alpha = 0.04f),
    glassSpecular = Color.White.copy(alpha = 0.10f),
    glassShadow = Color.Black.copy(alpha = 0.45f),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFFA9B0BC),
    textTertiary = Color(0xFF7C8494),
    accent = Color(0xFF5B8CFF),
    accentSecondary = Color(0xFF8E7CFF),
    onAccent = Color(0xFFFFFFFF),
    recording = Color(0xFFFF4D5E),
    success = Color(0xFF3DD68C),
    warning = Color(0xFFFFB547),
    error = Color(0xFFFF6B6B),
    auroraBlue = Color(0xFF2F5BFF),
    auroraViolet = Color(0xFF7A5CFF),
    auroraTeal = Color(0xFF1FA3B8),
)

internal val LightGlassColors = GlassColors(
    isDark = false,
    background = Color(0xFFEEF1F7),
    backgroundElevated = Color(0xFFFFFFFF),
    glassFill = Color.White.copy(alpha = 0.55f),
    glassFillStrong = Color.White.copy(alpha = 0.75f),
    glassBorderHighlight = Color.White.copy(alpha = 0.95f),
    glassBorderShade = Color(0xFF0B1020).copy(alpha = 0.08f),
    glassSpecular = Color.White.copy(alpha = 0.55f),
    glassShadow = Color(0xFF1B2440).copy(alpha = 0.16f),
    textPrimary = Color(0xFF0B0E14),
    textSecondary = Color(0xFF444C5C),
    textTertiary = Color(0xFF6A7282),
    accent = Color(0xFF2F63F0),
    accentSecondary = Color(0xFF6A55E8),
    onAccent = Color(0xFFFFFFFF),
    recording = Color(0xFFE5243B),
    success = Color(0xFF14A565),
    warning = Color(0xFFC77800),
    error = Color(0xFFD32F2F),
    auroraBlue = Color(0xFF8FB0FF),
    auroraViolet = Color(0xFFB9A8FF),
    auroraTeal = Color(0xFF8FDCE6),
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlassColors }
