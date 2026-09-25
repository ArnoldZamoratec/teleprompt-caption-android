package com.arnoldcode.glassprompt.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = TextStyle(fontFamily = FontFamily.Default)

/** Material 3 type scale tuned for a crisp, editorial feel (tighter headlines, relaxed body). */
internal val GlassTypography = Typography(
    displayLarge = Base.copy(fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    displayMedium = Base.copy(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    displaySmall = Base.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em),
    headlineLarge = Base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    headlineMedium = Base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    headlineSmall = Base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.005.em),
    titleSmall = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.005.em),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
    labelMedium = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em),
    labelSmall = Base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.03.em),
)
