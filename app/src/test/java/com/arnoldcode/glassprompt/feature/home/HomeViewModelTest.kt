package com.arnoldcode.glassprompt.feature.home

import app.cash.turbine.test
import com.arnoldcode.glassprompt.core.common.TimeProvider
import com.arnoldcode.glassprompt.domain.usecase.ObserveRecentExportsUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveRecentProjectsUseCase
import com.arnoldcode.glassprompt.testing.FakeExportRepository
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `greeting boundaries follow the time of day`() {
        assertThat(greetingFor(LocalTime.of(4, 59))).isEqualTo(Greeting.EVENING)
        assertThat(greetingFor(LocalTime.of(5, 0))).isEqualTo(Greeting.MORNING)
        assertThat(greetingFor(LocalTime.of(11, 59))).isEqualTo(Greeting.MORNING)
        assertThat(greetingFor(LocalTime.of(12, 0))).isEqualTo(Greeting.AFTERNOON)
        assertThat(greetingFor(LocalTime.of(19, 59))).isEqualTo(Greeting.AFTERNOON)
        assertThat(greetingFor(LocalTime.of(20, 0))).isEqualTo(Greeting.EVENING)
        assertThat(greetingFor(LocalTime.MIDNIGHT)).isEqualTo(Greeting.EVENING)
    }

    @Test
    fun `state combines the greeting with recent projects`() = runTest {
        val repository = FakeProjectRepository()
        repository.seed("Viejo", updatedAt = 1)
        repository.seed("Nuevo", updatedAt = 2)
        val viewModel = HomeViewModel(TimeProvider { LocalTime.of(9, 30) }, ObserveRecentProjectsUseCase(repository), ObserveRecentExportsUseCase(FakeExportRepository()))

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.greeting).isEqualTo(Greeting.MORNING)
            assertThat(state.recentProjects.map { it.name }).containsExactly("Nuevo", "Viejo").inOrder()
            assertThat(state.recentVideos).isEmpty()
        }
    }
}
