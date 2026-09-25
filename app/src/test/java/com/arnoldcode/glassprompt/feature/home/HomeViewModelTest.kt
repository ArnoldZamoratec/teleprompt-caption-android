package com.arnoldcode.glassprompt.feature.home

import com.arnoldcode.glassprompt.core.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class HomeViewModelTest {

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
    fun `initial state uses the injected clock and starts with empty lists`() {
        val viewModel = HomeViewModel(TimeProvider { LocalTime.of(9, 30) })

        val state = viewModel.uiState.value
        assertThat(state.greeting).isEqualTo(Greeting.MORNING)
        assertThat(state.recentProjects).isEmpty()
        assertThat(state.recentVideos).isEmpty()
    }
}
