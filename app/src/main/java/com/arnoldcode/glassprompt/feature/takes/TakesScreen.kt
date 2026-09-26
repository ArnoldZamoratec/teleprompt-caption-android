package com.arnoldcode.glassprompt.feature.takes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassCard
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveProjectTakesUseCase
import com.arnoldcode.glassprompt.feature.common.formatClock
import com.arnoldcode.glassprompt.feature.common.relativeTimeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import kotlin.math.min

@Immutable
data class TakesUiState(
    val isLoading: Boolean = true,
    val projectName: String = "",
    val takes: List<Take> = emptyList(),
)

/** Every recorded take of a project, newest first, so any of them can be captioned or exported later. */
@HiltViewModel
class TakesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getProject: GetProjectUseCase,
    observeTakes: ObserveProjectTakesUseCase,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle[ARG_PROJECT_ID]) { "projectId is required" }

    val uiState: StateFlow<TakesUiState> = combine(
        flow { emit(getProject(projectId).getOrNull()?.name.orEmpty()) },
        observeTakes(projectId),
    ) { name, takes ->
        // Takes whose file is gone (cleared storage) can't be opened: hide them.
        TakesUiState(isLoading = false, projectName = name, takes = takes.filter { File(it.filePath).exists() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TakesUiState())

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}

@Composable
fun TakesScreen(
    onBack: () -> Unit,
    onOpenTake: (takeId: String) -> Unit,
    onRecord: () -> Unit,
    viewModel: TakesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = GlassTheme.spacing
    val colors = GlassTheme.colors
    Column(Modifier.fillMaxSize().testTag("takes_screen")) {
        GlassTopBar(
            title = state.projectName.ifEmpty { stringResource(R.string.takes_title) },
            navigationIcon = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
        )
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
            state.takes.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                GlassEmptyState(
                    icon = Icons.Outlined.Videocam,
                    title = stringResource(R.string.takes_empty_title),
                    message = stringResource(R.string.takes_empty_body),
                    actionText = stringResource(R.string.editor_record),
                    onAction = onRecord,
                )
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.takes, key = { it.id }) { take ->
                        val number = state.takes.size - state.takes.indexOf(take)
                        val title = stringResource(R.string.takes_item_title, number)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().testTag("take_${take.id}"),
                            onClick = { onOpenTake(take.id) },
                            onClickLabel = title,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Icon(Icons.Outlined.Movie, contentDescription = null, tint = colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                    Text(
                                        stringResource(
                                            R.string.takes_item_info,
                                            relativeTimeLabel(take.createdAt),
                                            formatClock(take.durationMs),
                                            min(take.width, take.height),
                                            take.frameRate,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
