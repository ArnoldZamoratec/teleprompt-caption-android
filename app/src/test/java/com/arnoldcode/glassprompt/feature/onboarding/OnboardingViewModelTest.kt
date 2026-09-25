package com.arnoldcode.glassprompt.feature.onboarding

import com.arnoldcode.glassprompt.domain.usecase.CompleteOnboardingUseCase
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.arnoldcode.glassprompt.testing.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OnboardingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onStart persists completion and signals finished`() = runTest {
        val repository = FakeUserPreferencesRepository()
        val viewModel = OnboardingViewModel(CompleteOnboardingUseCase(repository), NoOpLogger)

        viewModel.onStart()

        assertThat(repository.preferences.value.onboardingCompleted).isTrue()
        assertThat(viewModel.finished.value).isTrue()
    }

    @Test
    fun `a storage failure still lets the user continue`() = runTest {
        val failing = mockk<CompleteOnboardingUseCase>()
        coEvery { failing.invoke() } throws IOException("disk full")
        val viewModel = OnboardingViewModel(failing, NoOpLogger)

        viewModel.onStart()

        assertThat(viewModel.finished.value).isTrue()
    }
}
