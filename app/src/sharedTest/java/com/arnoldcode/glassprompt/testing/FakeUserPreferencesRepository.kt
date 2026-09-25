package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory preferences shared by JVM and instrumented tests. */
class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {

    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<UserPreferences> = state

    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update { it.copy(onboardingCompleted = completed) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setReduceEffects(enabled: Boolean) = state.update { it.copy(reduceEffects = enabled) }
    override suspend fun setTranscriptionMode(mode: TranscriptionMode) = state.update { it.copy(transcriptionMode = mode) }
    override suspend fun setTranscriptionLanguage(language: String) = state.update { it.copy(transcriptionLanguage = language) }
    override suspend fun setDefaultCaptionPreset(preset: CaptionPreset) = state.update { it.copy(defaultCaptionPreset = preset) }

    fun reset(value: UserPreferences = UserPreferences()) {
        state.value = value
    }
}
