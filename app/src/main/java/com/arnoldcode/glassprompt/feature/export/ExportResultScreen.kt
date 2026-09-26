package com.arnoldcode.glassprompt.feature.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Subtitles
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.ExportedVideo
import com.arnoldcode.glassprompt.feature.common.VideoPlayerView
import com.arnoldcode.glassprompt.feature.common.formatClock
import com.arnoldcode.glassprompt.feature.common.rememberVideoPlayerState
import com.arnoldcode.glassprompt.feature.share.VideoSharing
import kotlin.math.min

@Composable
fun ExportResultScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ExportResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooserTitle = stringResource(R.string.result_share)
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportResultEvent.ShareSubtitles -> VideoSharing.shareSubtitles(context, event.path, chooserTitle)
            }
        }
    }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }
    // Android 8-9 write to Movies directly and need the storage permission; newer ones don't.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onSaveToGallery()
    }
    val save = {
        val needsAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.onSaveToGallery()
    }

    Box(Modifier.fillMaxSize().testTag("export_result_screen")) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = stringResource(R.string.result_title),
                navigationIcon = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
                actions = {
                    if (state.export != null) {
                        GlassIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.result_delete), viewModel::onDeleteRequest, tint = GlassTheme.colors.error)
                    }
                },
            )
            val export = state.export
            when {
                state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = GlassTheme.colors.accent)
                }
                export == null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    GlassEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.review_missing_title),
                        message = stringResource(R.string.review_missing_body),
                        actionText = stringResource(R.string.action_back),
                        onAction = onBack,
                    )
                }
                else -> ResultContent(export, state.isSaving, save, viewModel::onShareSubtitles, onDone, Modifier.weight(1f))
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (state.confirmDelete) {
        GlassDialog(
            title = stringResource(R.string.result_delete_title),
            message = stringResource(R.string.result_delete_body),
            onDismissRequest = viewModel::onDeleteDismiss,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = viewModel::onDeleteConfirm,
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun ResultContent(
    export: ExportedVideo,
    isSaving: Boolean,
    onSave: () -> Unit,
    onShareSubtitles: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val colors = GlassTheme.colors
    val context = LocalContext.current
    val player = rememberVideoPlayerState(export.filePath)
    val aspect = if (export.width > 0 && export.height > 0) export.width / export.height.toFloat() else 9f / 16f
    val targets = remember { VideoSharing.installedTargets(context) }
    val chooserTitle = stringResource(R.string.result_share)

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
                videoWidthFraction = if (aspect < 1f) 0.6f else 1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    R.string.result_info,
                    formatClock(export.durationMs),
                    min(export.width, export.height),
                    export.frameRate,
                    Formatter.formatShortFileSize(context, export.sizeBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            GlassButton(
                text = stringResource(R.string.result_share),
                icon = Icons.Outlined.IosShare,
                onClick = { player.pause(); VideoSharing.shareVideo(context, export.filePath, chooserTitle = chooserTitle) },
                modifier = Modifier.fillMaxWidth().testTag("result_share"),
            )
            if (targets.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    targets.forEach { target ->
                        GlassChip(
                            text = stringResource(target.label),
                            selected = false,
                            onClick = { player.pause(); VideoSharing.shareVideo(context, export.filePath, target, chooserTitle) },
                        )
                    }
                }
            }
            GlassButton(
                text = stringResource(if (export.galleryUri != null) R.string.result_saved_label else R.string.result_save),
                icon = if (export.galleryUri != null) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                onClick = onSave,
                enabled = !isSaving && export.galleryUri == null,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("result_save"),
            )
            GlassButton(
                text = stringResource(R.string.result_srt),
                icon = Icons.Outlined.Subtitles,
                onClick = onShareSubtitles,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton(
                text = stringResource(R.string.result_done),
                onClick = onDone,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
