package com.arnoldcode.glassprompt.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** App-wide user preferences. Per-project settings live in the database, not here. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val reduceEffects: Boolean = false,
    val transcriptionMode: TranscriptionMode = TranscriptionMode.AUTO,
    /** BCP-47 tag for speech recognition; empty = the device language. */
    val transcriptionLanguage: String = "",
    /** Look given to new caption tracks. */
    val defaultCaptionPreset: CaptionPreset = CaptionPreset.CLASSIC,
)
