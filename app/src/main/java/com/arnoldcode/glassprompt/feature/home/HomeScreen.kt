package com.arnoldcode.glassprompt.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextRotationNone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.arnoldcode.glassprompt.core.designsystem.component.GlassCard
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSectionHeader
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.feature.common.relativeTimeLabel

@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onImportScript: () -> Unit,
    onOpenTeleprompter: () -> Unit,
    onRecord: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onSeeAllProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        actions = HomeActions(
            onNewProject, onImportScript, onOpenTeleprompter, onRecord,
            onOpenProject, onOpenVideo, onSeeAllProjects, onOpenSettings,
        ),
    )
}

/** Groups Home callbacks so the content composable stays readable and previews stay cheap. */
internal data class HomeActions(
    val onNewProject: () -> Unit = {},
    val onImportScript: () -> Unit = {},
    val onOpenTeleprompter: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onOpenProject: (String) -> Unit = {},
    val onOpenVideo: (String) -> Unit = {},
    val onSeeAllProjects: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
internal fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val spacing = GlassTheme.spacing
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val shell = LocalShellContentPadding.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = PaddingValues(
                top = safe.calculateTopPadding() + spacing.md,
                bottom = shell.calculateBottomPadding() + spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") { HomeHeader(state.greeting, actions.onOpenSettings, Modifier.padding(horizontal = spacing.lg)) }
            item(key = "hero") { NewProjectHero(actions.onNewProject, Modifier.padding(horizontal = spacing.lg)) }
            item(key = "quick") { QuickActions(actions, Modifier.padding(horizontal = spacing.lg)) }
            item(key = "projects") {
                RecentProjectsSection(state.recentProjects, actions)
            }
            item(key = "videos") {
                RecentVideosSection(state.recentVideos, actions.onOpenVideo)
            }
        }
    }
}

@Composable
private fun HomeHeader(greeting: Greeting, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(greeting.labelRes()),
                style = MaterialTheme.typography.headlineLarge,
                color = GlassTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.colors.textSecondary,
            )
        }
        GlassIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.home_open_settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun NewProjectHero(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_new_project"),
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_new_project_action),
        shape = GlassTheme.shapes.extraLarge,
        depth = GlassTheme.elevation.floating,
        tint = colors.accent.copy(alpha = 0.16f),
        contentPadding = PaddingValues(GlassTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.accent, colors.accentSecondary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.TextRotationNone, contentDescription = null, tint = colors.onAccent)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textSecondary)
        }
        Spacer(Modifier.height(GlassTheme.spacing.md))
        Text(stringResource(R.string.home_new_project), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        Spacer(Modifier.height(GlassTheme.spacing.xxs))
        Text(stringResource(R.string.home_new_project_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun QuickActions(actions: HomeActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm)) {
        QuickActionCard(Icons.Outlined.FileOpen, stringResource(R.string.home_quick_import), actions.onImportScript, Modifier.weight(1f))
        QuickActionCard(Icons.Outlined.Description, stringResource(R.string.home_quick_teleprompter), actions.onOpenTeleprompter, Modifier.weight(1f))
        QuickActionCard(Icons.Outlined.Videocam, stringResource(R.string.home_quick_record), actions.onRecord, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionCard(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        shape = GlassTheme.shapes.large,
        contentPadding = PaddingValues(vertical = GlassTheme.spacing.md, horizontal = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = GlassTheme.colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(GlassTheme.spacing.sm))
        Text(label, style = MaterialTheme.typography.labelMedium, color = GlassTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentProjectsSection(projects: List<ProjectSummary>, actions: HomeActions) {
    val spacing = GlassTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        GlassSectionHeader(
            title = stringResource(R.string.home_recent_projects),
            actionText = if (projects.isNotEmpty()) stringResource(R.string.action_see_all) else null,
            onAction = actions.onSeeAllProjects,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        if (projects.isEmpty()) {
            GlassEmptyState(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.home_empty_projects_title),
                message = stringResource(R.string.home_empty_projects_body),
                actionText = stringResource(R.string.home_new_project),
                onAction = actions.onNewProject,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(projects, key = { it.id }) { project -> ProjectCard(project, actions.onOpenProject) }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectSummary, onOpen: (String) -> Unit) {
    GlassCard(
        modifier = Modifier.width(200.dp),
        onClick = { onOpen(project.id) },
        onClickLabel = project.name,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = GlassTheme.colors.accentSecondary)
        Spacer(Modifier.height(GlassTheme.spacing.sm))
        Text(project.name, style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = pluralStringResource(R.plurals.home_project_words, project.wordCount, project.wordCount) + " · " + relativeTimeLabel(project.updatedAt),
            style = MaterialTheme.typography.bodySmall,
            color = GlassTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentVideosSection(videos: List<VideoSummaryUi>, onOpenVideo: (String) -> Unit) {
    val spacing = GlassTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        GlassSectionHeader(title = stringResource(R.string.home_recent_videos), modifier = Modifier.padding(horizontal = spacing.lg))
        if (videos.isEmpty()) {
            GlassEmptyState(
                icon = Icons.Outlined.Movie,
                title = stringResource(R.string.home_empty_videos_title),
                message = stringResource(R.string.home_empty_videos_body),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(videos, key = { it.id }) { video ->
                    GlassCard(modifier = Modifier.width(160.dp), onClick = { onOpenVideo(video.id) }, onClickLabel = video.title) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(GlassTheme.shapes.medium)
                                .background(GlassTheme.colors.glassFillStrong),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Movie, contentDescription = null, tint = GlassTheme.colors.textTertiary)
                        }
                        Spacer(Modifier.height(GlassTheme.spacing.xs))
                        Text(video.title, style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(video.durationLabel, style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

private fun Greeting.labelRes(): Int = when (this) {
    Greeting.MORNING -> R.string.home_greeting_morning
    Greeting.AFTERNOON -> R.string.home_greeting_afternoon
    Greeting.EVENING -> R.string.home_greeting_evening
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun HomePreviewEmpty() {
    GlassTheme(darkTheme = true) {
        GlassBackdrop { HomeContent(HomeUiState(Greeting.MORNING), HomeActions()) }
    }
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun HomePreviewWithData() {
    val state = HomeUiState(
        greeting = Greeting.EVENING,
        recentProjects = listOf(
            ProjectSummary("1", "Reseña cámara", 820, System.currentTimeMillis() - 7_200_000),
            ProjectSummary("2", "Tutorial Kotlin", 1245, System.currentTimeMillis() - 86_400_000),
        ),
        recentVideos = listOf(VideoSummaryUi("v1", "Reseña cámara", "1:24", null)),
    )
    GlassTheme(darkTheme = false) {
        GlassBackdrop { HomeContent(state, HomeActions()) }
    }
}
