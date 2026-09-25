package com.arnoldcode.glassprompt.feature.settings

import app.cash.turbine.test
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.usecase.ObserveUserPreferencesUseCase
import com.arnoldcode.glassprompt.domain.transcription.SpeechModelStatus
import com.arnoldcode.glassprompt.domain.transcription.SpeechModels
import com.arnoldcode.glassprompt.domain.usecase.SpeechModelUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateAppearanceUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateTranscriptionPreferencesUseCase
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserPreferencesRepository()
    private val speechModels = FakeSpeechModels()
    // Lazy: the ViewModel launches in init, so it must be built after the rule has swapped Main.
    private val viewModel by lazy {
        SettingsViewModel(
            ObserveUserPreferencesUseCase(repository),
            UpdateAppearanceUseCase(repository),
            UpdateTranscriptionPreferencesUseCase(repository),
            SpeechModelUseCase(speechModels),
        )
    }

    @Test
    fun `theme and reduce-effects changes are persisted and reflected`() = runTest {
        viewModel.preferences.test {
            assertThat(awaitItem()).isEqualTo(UserPreferences())

            viewModel.onThemeModeSelected(ThemeMode.LIGHT)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.LIGHT)

            viewModel.onReduceEffectsChanged(true)
            assertThat(awaitItem().reduceEffects).isTrue()
        }
        assertThat(repository.preferences.value)
            .isEqualTo(UserPreferences(themeMode = ThemeMode.LIGHT, reduceEffects = true))
    }

    @Test
    fun `speech model status follows the voice language and download is requested on demand`() = runTest(mainDispatcherRule.dispatcher) {
        repository.setTranscriptionLanguage("es-ES")
        viewModel.onResume()
        advanceUntilIdle()
        assertThat(viewModel.speechModelStatus.value).isEqualTo(SpeechModelStatus.DOWNLOADABLE)
        assertThat(speechModels.checked.last()).isEqualTo("es-ES")

        viewModel.onDownloadSpeechModel()
        advanceUntilIdle()
        assertThat(speechModels.downloads).isEqualTo(listOf("es-ES"))
        assertThat(viewModel.speechModelStatus.value).isEqualTo(SpeechModelStatus.DOWNLOADING)
    }

    private class FakeSpeechModels : SpeechModels {
        val checked = mutableListOf<String>()
        val downloads = mutableListOf<String>()

        override suspend fun status(language: String): SpeechModelStatus {
            checked += language
            return if (language in downloads) SpeechModelStatus.DOWNLOADING else SpeechModelStatus.DOWNLOADABLE
        }

        override suspend fun requestDownload(language: String) {
            downloads += language
        }
    }
}
