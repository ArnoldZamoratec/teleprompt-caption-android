package com.arnoldcode.glassprompt.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.arnoldcode.glassprompt.core.common.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.domain.usecase.ObserveRecentExportsUseCase
import com.arnoldcode.glassprompt.domain.usecase.VideoThumbnailUseCase
import com.arnoldcode.glassprompt.domain.usecase.exportThumbnailKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.arnoldcode.glassprompt.domain.usecase.ObserveRecentProjectsUseCase
import com.arnoldcode.glassprompt.feature.common.formatClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
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

/** Home state: greeting, recent projects and recently exported videos, all from Room. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    timeProvider: TimeProvider,
    observeRecentProjects: ObserveRecentProjectsUseCase,
    observeRecentExports: ObserveRecentExportsUseCase,
    private val thumbnail: VideoThumbnailUseCase,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val greeting = greetingFor(timeProvider.now())

    /** Poster path per export id, filled in the background as posters become ready. */
    private val posters = MutableStateFlow<Map<String, String>>(emptyMap())
    private val requested = mutableSetOf<String>()

    private val availableExports = observeRecentExports(RECENT_LIMIT)
        // Exports deleted from disk behind our back are not offered. Disk checks stay off the main thread.
        .map { exports -> exports.filter { File(it.filePath).exists() } }
        .flowOn(dispatchers.io)
        .onEach(::requestPosters)

    val uiState: StateFlow<HomeUiState> = combine(
        observeRecentProjects(RECENT_LIMIT),
        availableExports,
        posters,
    ) { projects, exports, posters ->
        HomeUiState(
            greeting = greeting,
            recentProjects = projects,
            recentVideos = exports.map { export ->
                VideoSummaryUi(export.id, export.projectName, formatClock(export.durationMs), thumbnailPath = posters[export.id])
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(greeting))

    /** Each poster is requested once per ViewModel; the store itself caches them across launches. */
    private fun requestPosters(exports: List<ExportedVideo>) {
        exports.filter { requested.add(it.id) }.forEach { export ->
            viewModelScope.launch {
                thumbnail(export.filePath, exportThumbnailKey(export.id))?.let { path -> posters.update { it + (export.id to path) } }
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 10
    }
}
