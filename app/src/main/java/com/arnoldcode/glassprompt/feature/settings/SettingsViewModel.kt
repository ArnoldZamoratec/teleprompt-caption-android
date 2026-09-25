package com.arnoldcode.glassprompt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.usecase.ObserveUserPreferencesUseCase
import com.arnoldcode.glassprompt.domain.transcription.SpeechModelStatus
import com.arnoldcode.glassprompt.domain.usecase.SpeechModelUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateAppearanceUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateTranscriptionPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observePreferences: ObserveUserPreferencesUseCase,
    private val updateAppearance: UpdateAppearanceUseCase,
    private val updateTranscription: UpdateTranscriptionPreferencesUseCase,
    private val speechModel: SpeechModelUseCase,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    /** Latest voice language setting ("" = device language). */
    private var voiceLanguage = ""

    private val _speechModelStatus = MutableStateFlow<SpeechModelStatus?>(null)

    /** Offline model of the chosen voice language; null while checking. */
    val speechModelStatus: StateFlow<SpeechModelStatus?> = _speechModelStatus.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().map { it.transcriptionLanguage }.distinctUntilChanged().collect { language ->
                voiceLanguage = language
                refreshSpeechModel()
            }
        }
    }

    /** Re-checks the model, e.g. when returning from the system's download prompt. */
    fun onResume() {
        viewModelScope.launch { refreshSpeechModel() }
    }

    fun onDownloadSpeechModel() {
        viewModelScope.launch {
            speechModel.download(voiceLanguage)
            refreshSpeechModel()
        }
    }

    private suspend fun refreshSpeechModel() {
        _speechModelStatus.value = speechModel.status(voiceLanguage)
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { updateAppearance.setThemeMode(mode) }
    }

    fun onReduceEffectsChanged(enabled: Boolean) {
        viewModelScope.launch { updateAppearance.setReduceEffects(enabled) }
    }

    fun onTranscriptionModeSelected(mode: TranscriptionMode) {
        viewModelScope.launch { updateTranscription.setMode(mode) }
    }

    fun onTranscriptionLanguageSelected(language: String) {
        viewModelScope.launch { updateTranscription.setLanguage(language) }
    }

    companion object {
        /** Languages offered for speech recognition; "" = the device language. */
        val TranscriptionLanguages = listOf("", "es-ES", "es-US", "en-US", "pt-BR", "fr-FR", "it-IT", "de-DE")
    }
}
