package com.arnoldcode.glassprompt.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** App-wide user preferences. Per-project settings live in the database, not here. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val reduceEffects: Boolean = false,
)
