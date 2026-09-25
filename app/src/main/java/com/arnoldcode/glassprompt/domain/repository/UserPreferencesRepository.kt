package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setReduceEffects(enabled: Boolean)
    suspend fun setTranscriptionMode(mode: TranscriptionMode)
    suspend fun setTranscriptionLanguage(language: String)
    suspend fun setDefaultCaptionPreset(preset: CaptionPreset)
}
