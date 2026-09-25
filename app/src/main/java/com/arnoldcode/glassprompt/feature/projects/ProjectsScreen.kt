package com.arnoldcode.glassprompt.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassCard
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTextField
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.feature.common.relativeTimeLabel

@Composable
fun ProjectsScreen(
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onEditProject: (String) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectsContent(
        state = state,
        actions = ProjectsActions(
            onNewProject = onNewProject,
            onOpenProject = onOpenProject,
            onEditProject = onEditProject,
            onQueryChange = viewModel::onQueryChange,
            onDuplicate = viewModel::onDuplicate,
            onDeleteRequest = viewModel::onDeleteRequest,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

internal data class ProjectsActions(
    val onNewProject: () -> Unit = {},
    val onOpenProject: (String) -> Unit = {},
    val onEditProject: (String) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onDeleteRequest: (ProjectSummary) -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
internal fun ProjectsContent(state: ProjectsUiState, actions: ProjectsActions, modifier: Modifier = Modifier) {
    val spacing = GlassTheme.spacing
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val shell = LocalShellContentPadding.current
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Box(modifier.fillMaxSize().testTag("projects_screen"), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = safe.calculateTopPadding() + spacing.md,
                bottom = shell.calculateBottomPadding() + spacing.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { Header(actions.onNewProject) }
            val projects = state.projects
            if (projects == null) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(spacing.xl), Alignment.Center) {
                        CircularProgressIndicator(color = GlassTheme.colors.accent)
                    }
                }
                return@LazyVerticalGrid
            }
            if (projects.isNotEmpty() || state.query.isNotEmpty()) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    GlassTextField(
                        value = state.query,
                        onValueChange = actions.onQueryChange,
                        placeholder = stringResource(R.string.projects_search),
                        modifier = Modifier.fillMaxWidth().testTag("projects_search"),
                    )
                }
            }
            when {
                projects.isEmpty() && state.query.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    GlassEmptyState(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.projects_empty_title),
                        message = stringResource(R.string.projects_empty_body),
                        actionText = stringResource(R.string.home_new_project),
                        onAction = actions.onNewProject,
                    )
                }
                projects.isEmpty() -> item(key = "no_results", span = { GridItemSpan(maxLineSpan) }) {
                    GlassEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.projects_no_results_title),
                        message = stringResource(R.string.projects_no_results_body, state.query),
                    )
                }
                else -> items(projects, key = { it.id }) { project -> ProjectRow(project, actions) }
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = shell.calculateBottomPadding()))
    }

    state.pendingDelete?.let { project ->
        GlassDialog(
            title = stringResource(R.string.projects_delete_title),
            message = stringResource(R.string.projects_delete_body, project.name),
            onDismissRequest = actions.onDeleteDismiss,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = actions.onDeleteConfirm,
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun Header(onNewProject: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.nav_projects),
            style = MaterialTheme.typography.headlineLarge,
            color = GlassTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        GlassButton(
            text = stringResource(R.string.projects_new),
            icon = Icons.Outlined.Add,
            onClick = onNewProject,
            modifier = Modifier.testTag("projects_new"),
        )
    }
}

@Composable
private fun ProjectRow(project: ProjectSummary, actions: ProjectsActions) {
    val colors = GlassTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("project_${project.id}"),
        onClick = { actions.onOpenProject(project.id) },
        onClickLabel = stringResource(R.string.projects_open_script),
        contentPadding = PaddingValues(start = GlassTheme.spacing.md, top = GlassTheme.spacing.sm, bottom = GlassTheme.spacing.sm, end = GlassTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.accentSecondary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(GlassTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.home_project_words, project.wordCount, project.wordCount) + " · " + relativeTimeLabel(project.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                GlassIconButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.projects_more_actions, project.name),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.backgroundElevated,
                    shape = GlassTheme.shapes.medium,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_action_settings)) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onEditProject(project.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_action_duplicate)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onDuplicate(project.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = colors.error) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.error) },
                        onClick = { menuOpen = false; actions.onDeleteRequest(project) },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProjectsPreview() {
    val now = System.currentTimeMillis()
    GlassTheme(darkTheme = true) {
        GlassBackdrop {
            ProjectsContent(
                ProjectsUiState(
                    projects = listOf(
                        ProjectSummary("1", "Reseña cámara", 820, now - 2 * 3_600_000),
                        ProjectSummary("2", "Tutorial Kotlin", 1245, now - 86_400_000),
                    ),
                ),
                ProjectsActions(),
            )
        }
    }
}
