package com.arnoldcode.glassprompt.feature.settings

import app.cash.turbine.test
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.usecase.ObserveUserPreferencesUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateAppearanceUseCase
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserPreferencesRepository()
    private val viewModel = SettingsViewModel(
        ObserveUserPreferencesUseCase(repository),
        UpdateAppearanceUseCase(repository),
    )

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
}
