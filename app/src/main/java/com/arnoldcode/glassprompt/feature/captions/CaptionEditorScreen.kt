package com.arnoldcode.glassprompt.feature.captions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTextField
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.transcription.ScriptAlignmentEngine
import com.arnoldcode.glassprompt.feature.common.VideoPlayerState
import com.arnoldcode.glassprompt.feature.common.VideoPlayerView
import com.arnoldcode.glassprompt.feature.common.rememberVideoPlayerState

private enum class EditorTab { TEXT, STYLE }

@Composable
fun CaptionEditorScreen(
    onBack: () -> Unit,
    onExport: (takeId: String) -> Unit,
    viewModel: CaptionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }
    // Save pending edits as soon as the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().testTag("caption_editor_screen")) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = stringResource(R.string.captions_title),
                navigationIcon = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
                actions = {
                    if (state.hasTrack) {
                        GlassIconButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.captions_undo), viewModel::onUndo, enabled = state.canUndo)
                        GlassIconButton(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.captions_redo), viewModel::onRedo, enabled = state.canRedo)
                        GlassIconButton(Icons.Outlined.Refresh, stringResource(R.string.captions_retranscribe), viewModel::onRetranscribeRequest, enabled = !state.isTranscribing)
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
                else -> EditorContent(state, take, viewModel, onExport, Modifier.weight(1f))
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (state.confirmRetranscribe) {
        GlassDialog(
            title = stringResource(R.string.captions_retranscribe_title),
            message = stringResource(R.string.captions_retranscribe_body),
            onDismissRequest = viewModel::onRetranscribeDismiss,
            confirmText = stringResource(R.string.captions_retranscribe_voice),
            onConfirm = { viewModel.onRetranscribe(TranscriptionMode.AUTO) },
            dismissText = stringResource(R.string.captions_use_script),
            onDismiss = { viewModel.onRetranscribe(TranscriptionMode.SCRIPT) },
        )
    }
}

@Composable
private fun EditorContent(
    state: CaptionEditorUiState,
    take: Take,
    viewModel: CaptionEditorViewModel,
    onExport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val player = rememberVideoPlayerState(take.filePath)
    val aspect = if (take.width > 0 && take.height > 0) take.width / take.height.toFloat() else 9f / 16f
    var tab by rememberSaveable { mutableStateOf(EditorTab.TEXT) }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            VideoPlayerView(
                state = player,
                aspectRatio = aspect,
                // Portrait video: keep room for the editor below.
                videoWidthFraction = if (aspect < 1f) 0.52f else 1f,
                modifier = Modifier.padding(horizontal = spacing.lg).fillMaxWidth(),
                overlay = {
                    CaptionOverlay(state.captions, state.style, positionMs = { player.positionMs }, animated = { player.isPlaying })
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                TranscriptionStatus(state, viewModel, Modifier.padding(horizontal = spacing.lg))
                if (state.hasTrack) {
                    Row(Modifier.padding(horizontal = spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        GlassChip(stringResource(R.string.captions_tab_text), tab == EditorTab.TEXT, { tab = EditorTab.TEXT })
                        GlassChip(stringResource(R.string.captions_tab_style), tab == EditorTab.STYLE, { tab = EditorTab.STYLE })
                    }
                    when (tab) {
                        EditorTab.TEXT -> TextTab(state, player, viewModel)
                        EditorTab.STYLE -> CaptionStylePanel(
                            style = state.style,
                            onPreset = viewModel::onPreset,
                            onChange = viewModel::onStyleChange,
                            onWordsPerLine = viewModel::onWordsPerLine,
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
                    GlassButton(
                        text = stringResource(R.string.captions_export),
                        icon = Icons.Outlined.IosShare,
                        onClick = {
                            player.pause()
                            viewModel.flush()
                            onExport(take.id)
                        },
                        enabled = !state.isTranscribing,
                        modifier = Modifier.padding(horizontal = spacing.lg).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptionStatus(state: CaptionEditorUiState, viewModel: CaptionEditorViewModel, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    when (val transcription = state.transcription) {
        is TranscriptionState.Running -> GlassPanel(modifier.fillMaxWidth().testTag("transcription_progress")) {
            Text(stringResource(transcription.stage.labelRes()), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            LinearProgressIndicator(
                progress = { transcription.progress },
                color = colors.accent,
                trackColor = colors.glassFillStrong,
                modifier = Modifier.fillMaxWidth().padding(vertical = GlassTheme.spacing.xs),
            )
            Text(stringResource(R.string.captions_running_body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        is TranscriptionState.Failed -> GlassPanel(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.warning)
                Text(stringResource(R.string.captions_failed_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            }
            Text(stringResource(transcription.reason.labelRes()), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Row(Modifier.padding(top = GlassTheme.spacing.xs), horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
                GlassButton(stringResource(R.string.action_retry), { viewModel.onRetranscribe(TranscriptionMode.AUTO) }, style = GlassButtonStyle.Secondary)
                GlassButton(stringResource(R.string.captions_use_script), { viewModel.onRetranscribe(TranscriptionMode.SCRIPT) }, style = GlassButtonStyle.Secondary)
            }
        }
        else -> if (!state.hasTrack) {
            GlassEmptyState(
                icon = Icons.Outlined.ClosedCaption,
                title = stringResource(R.string.captions_empty_title),
                message = stringResource(R.string.captions_empty_body),
                actionText = stringResource(R.string.captions_generate),
                onAction = { viewModel.onRetranscribe(null) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TextTab(state: CaptionEditorUiState, player: VideoPlayerState, viewModel: CaptionEditorViewModel) {
    val spacing = GlassTheme.spacing
    val colors = GlassTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CaptionTimeline(
            captions = state.captions,
            selectedId = state.selectedId,
            positionMs = { player.positionMs },
            isPlaying = player.isPlaying,
            onSelect = { caption ->
                viewModel.onSelect(caption.id)
                player.seekTo(caption.startMs)
            },
        )
        Text(
            stringResource(if (state.engineId == ScriptAlignmentEngine.ID) R.string.captions_engine_script else R.string.captions_engine_speech),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        val selected = state.selected
        if (selected == null) {
            Text(stringResource(R.string.captions_select_hint), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(horizontal = spacing.lg))
            val newCaption = stringResource(R.string.captions_new_caption)
            GlassButton(
                text = stringResource(R.string.captions_add),
                icon = Icons.Outlined.Add,
                onClick = { viewModel.onInsertAt(player.positionMs, newCaption) },
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        } else {
            SelectedCaptionPanel(selected, player, viewModel, Modifier.padding(horizontal = spacing.lg))
        }
    }
}

@Composable
private fun SelectedCaptionPanel(caption: Caption, player: VideoPlayerState, viewModel: CaptionEditorViewModel, modifier: Modifier = Modifier) {
    val spacing = GlassTheme.spacing
    GlassPanel(modifier.fillMaxWidth().testTag("selected_caption")) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            GlassTextField(
                value = caption.text,
                onValueChange = { viewModel.onTextChange(caption.id, it) },
                label = stringResource(R.string.captions_caption_text),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("caption_text"),
            )
            TimeRow(
                label = stringResource(R.string.captions_start),
                timeMs = caption.startMs,
                onEarlier = { viewModel.onNudge(caption.id, -NUDGE_MS, 0) },
                onLater = { viewModel.onNudge(caption.id, NUDGE_MS, 0) },
                onToPlayhead = { viewModel.onSetStart(caption.id, player.positionMs) },
            )
            TimeRow(
                label = stringResource(R.string.captions_end),
                timeMs = caption.endMs,
                onEarlier = { viewModel.onNudge(caption.id, 0, -NUDGE_MS) },
                onLater = { viewModel.onNudge(caption.id, 0, NUDGE_MS) },
                onToPlayhead = { viewModel.onSetEnd(caption.id, player.positionMs) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                GlassIconButton(Icons.Outlined.ContentCut, stringResource(R.string.captions_split), { viewModel.onSplit(caption.id, player.positionMs) })
                GlassIconButton(Icons.Outlined.CallMerge, stringResource(R.string.captions_merge), { viewModel.onMergeWithNext(caption.id) })
                GlassIconButton(Icons.Outlined.DeleteOutline, stringResource(R.string.captions_delete), { viewModel.onDelete(caption.id) }, tint = GlassTheme.colors.error)
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, timeMs: Long, onEarlier: () -> Unit, onLater: () -> Unit, onToPlayhead: () -> Unit) {
    val colors = GlassTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
        Text("$label  ${formatPrecise(timeMs)}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        GlassIconButton(Icons.Outlined.FastRewind, "$label: ${stringResource(R.string.captions_earlier)}", onEarlier, size = 40.dp)
        GlassIconButton(Icons.Outlined.FastForward, "$label: ${stringResource(R.string.captions_later)}", onLater, size = 40.dp)
        GlassIconButton(Icons.Outlined.VerticalAlignCenter, "$label: ${stringResource(R.string.captions_set_to_playhead)}", onToPlayhead, size = 40.dp)
    }
}

private const val NUDGE_MS = 100L

private fun TranscriptionState.Stage.labelRes(): Int = when (this) {
    TranscriptionState.Stage.EXTRACTING_AUDIO -> R.string.captions_stage_extracting
    TranscriptionState.Stage.DETECTING_SPEECH -> R.string.captions_stage_detecting
    TranscriptionState.Stage.TRANSCRIBING -> R.string.captions_stage_transcribing
    TranscriptionState.Stage.SAVING -> R.string.captions_stage_saving
}

private fun TranscriptionState.Reason.labelRes(): Int = when (this) {
    TranscriptionState.Reason.NO_AUDIO -> R.string.captions_failed_no_audio
    TranscriptionState.Reason.NO_SPEECH -> R.string.captions_failed_no_speech
    TranscriptionState.Reason.ENGINE_ERROR -> R.string.captions_failed_engine
    TranscriptionState.Reason.NOT_FOUND -> R.string.captions_failed_not_found
}
