package com.arnoldcode.glassprompt.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.arnoldcode.glassprompt.core.common.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import javax.inject.Inject

enum class Greeting { MORNING, AFTERNOON, EVENING }

internal fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
    in 5..11 -> Greeting.MORNING
    in 12..19 -> Greeting.AFTERNOON
    else -> Greeting.EVENING
}

@Immutable
data class ProjectSummaryUi(
    val id: String,
    val name: String,
    val wordCount: Int,
    val updatedLabel: String,
)

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
    val recentProjects: List<ProjectSummaryUi> = emptyList(),
    val recentVideos: List<VideoSummaryUi> = emptyList(),
)

/**
 * Home state. Recent projects and videos are wired to Room in Phase 4; until then the
 * lists are empty and the screen shows its empty states.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(greeting = greetingFor(timeProvider.now())))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
