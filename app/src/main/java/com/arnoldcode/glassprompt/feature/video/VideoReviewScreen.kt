package com.arnoldcode.glassprompt.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.feature.common.VideoPlayerView
import com.arnoldcode.glassprompt.feature.common.formatClock
import com.arnoldcode.glassprompt.feature.common.rememberVideoPlayerState

@Composable
fun VideoReviewScreen(
    onBack: () -> Unit,
    onRecordAgain: () -> Unit,
    onCaptions: (takeId: String) -> Unit,
    onExport: (takeId: String) -> Unit,
    viewModel: VideoReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.deleted) { if (state.deleted) onRecordAgain() }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    Box(Modifier.fillMaxSize().testTag("video_review_screen")) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = state.projectName.ifEmpty { stringResource(R.string.review_title) },
                navigationIcon = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
                actions = {
                    if (state.take != null) {
                        GlassIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.review_delete), viewModel::onDeleteRequest, tint = GlassTheme.colors.error)
                    }
                },
            )
            val take = state.take
            when {
                state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = GlassTheme.colors.accent)
                }
                take == null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    GlassEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.review_missing_title),
                        message = stringResource(R.string.review_missing_body),
                        actionText = stringResource(R.string.action_back),
                        onAction = onBack,
                    )
                }
                else -> ReviewContent(take, onCaptions, onExport, onRecordAgain, Modifier.weight(1f))
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (state.confirmDelete) {
        GlassDialog(
            title = stringResource(R.string.review_delete_title),
            message = stringResource(R.string.review_delete_body),
            onDismissRequest = viewModel::onDeleteDismiss,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = viewModel::onDeleteConfirm,
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun ReviewContent(
    take: Take,
    onCaptions: (String) -> Unit,
    onExport: (String) -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val player = rememberVideoPlayerState(take.filePath)
    val aspect = if (take.width > 0 && take.height > 0) take.width / take.height.toFloat() else 9f / 16f
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            VideoPlayerView(
                state = player,
                aspectRatio = aspect,
                // Portrait videos would push the actions off-screen: cap their height.
                videoWidthFraction = if (aspect < 1f) 0.72f else 1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.review_info, formatClock(take.durationMs), minOf(take.width, take.height), take.frameRate),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            GlassButton(
                text = stringResource(R.string.review_captions),
                icon = Icons.Outlined.ClosedCaption,
                onClick = { player.pause(); onCaptions(take.id) },
                modifier = Modifier.fillMaxWidth().testTag("review_captions"),
            )
            GlassButton(
                text = stringResource(R.string.review_export_plain),
                icon = Icons.Outlined.IosShare,
                onClick = { player.pause(); onExport(take.id) },
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton(
                text = stringResource(R.string.review_record_again),
                icon = Icons.Outlined.Videocam,
                onClick = onRecordAgain,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
