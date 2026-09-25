package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import com.arnoldcode.glassprompt.domain.transcription.SpeechModelStatus
import com.arnoldcode.glassprompt.domain.transcription.SpeechModels
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

class ObserveUserPreferencesUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = repository.preferences
}

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke() = repository.setOnboardingCompleted(true)
}

class UpdateAppearanceUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    suspend fun setReduceEffects(enabled: Boolean) = repository.setReduceEffects(enabled)
}

/** Offline speech model of the language captions are recognized in (device language when unset). */
class SpeechModelUseCase @Inject constructor(
    private val models: SpeechModels,
) {
    suspend fun status(preferredLanguage: String): SpeechModelStatus = models.status(effective(preferredLanguage))
    suspend fun download(preferredLanguage: String) = models.requestDownload(effective(preferredLanguage))

    private fun effective(language: String) = language.ifBlank { Locale.getDefault().toLanguageTag() }
}

class UpdateTranscriptionPreferencesUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend fun setMode(mode: TranscriptionMode) = repository.setTranscriptionMode(mode)

    /** Empty = follow the device language. */
    suspend fun setLanguage(language: String) = repository.setTranscriptionLanguage(language)
}
