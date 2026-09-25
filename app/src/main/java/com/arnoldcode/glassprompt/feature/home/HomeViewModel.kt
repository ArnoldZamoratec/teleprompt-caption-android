package com.arnoldcode.glassprompt.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.arnoldcode.glassprompt.core.common.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.usecase.ObserveRecentProjectsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

enum class Greeting { MORNING, AFTERNOON, EVENING }

internal fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
    in 5..11 -> Greeting.MORNING
    in 12..19 -> Greeting.AFTERNOON
    else -> Greeting.EVENING
}

@Immutable
data class VideoSummaryUi(
    val id: String,
    val title: String,
    val durationLabel: String,
    val thumbnailPath: String?,
)

@Immutable
data class HomeUiState(
    val greeting: Greeting,
    val recentProjects: List<ProjectSummary> = emptyList(),
    val recentVideos: List<VideoSummaryUi> = emptyList(),
)

/** Home state: greeting plus recent projects from Room. Recent videos arrive with export (Phase 7). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    timeProvider: TimeProvider,
    observeRecentProjects: ObserveRecentProjectsUseCase,
) : ViewModel() {

    private val greeting = greetingFor(timeProvider.now())

    val uiState: StateFlow<HomeUiState> = observeRecentProjects(RECENT_LIMIT)
        .map { HomeUiState(greeting = greeting, recentProjects = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(greeting))

    private companion object {
        const val RECENT_LIMIT = 10
    }
}
