package com.arnoldcode.glassprompt.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            // A corrupted/unreadable file must never crash the app: fall back to defaults.
            if (error is IOException) {
                logger.e(TAG, "Unable to read preferences, using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.OnboardingCompleted] = completed }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.ThemeMode] = mode.name }
    }

    override suspend fun setReduceEffects(enabled: Boolean) {
        dataStore.edit { it[Keys.ReduceEffects] = enabled }
    }

    override suspend fun setTranscriptionMode(mode: TranscriptionMode) {
        dataStore.edit { it[Keys.TranscriptionMode] = mode.name }
    }

    override suspend fun setTranscriptionLanguage(language: String) {
        dataStore.edit { it[Keys.TranscriptionLanguage] = language }
    }

    override suspend fun setDefaultCaptionPreset(preset: CaptionPreset) {
        dataStore.edit { it[Keys.DefaultCaptionPreset] = preset.name }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            onboardingCompleted = this[Keys.OnboardingCompleted] ?: defaults.onboardingCompleted,
            themeMode = enumOrNull<ThemeMode>(this[Keys.ThemeMode]) ?: defaults.themeMode,
            reduceEffects = this[Keys.ReduceEffects] ?: defaults.reduceEffects,
            transcriptionMode = enumOrNull<TranscriptionMode>(this[Keys.TranscriptionMode]) ?: defaults.transcriptionMode,
            transcriptionLanguage = this[Keys.TranscriptionLanguage] ?: defaults.transcriptionLanguage,
            defaultCaptionPreset = enumOrNull<CaptionPreset>(this[Keys.DefaultCaptionPreset]) ?: defaults.defaultCaptionPreset,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(stored: String?): E? =
        stored?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private object Keys {
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ReduceEffects = booleanPreferencesKey("reduce_effects")
        val TranscriptionMode = stringPreferencesKey("transcription_mode")
        val TranscriptionLanguage = stringPreferencesKey("transcription_language")
        val DefaultCaptionPreset = stringPreferencesKey("default_caption_preset")
    }

    private companion object {
        const val TAG = "Preferences"
    }
}
