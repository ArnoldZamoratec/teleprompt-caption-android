package com.arnoldcode.glassprompt.feature.teleprompter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassFloatingButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.TextPosition
import com.arnoldcode.glassprompt.domain.script.ScriptStats
import com.arnoldcode.glassprompt.feature.common.durationLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen rehearsal teleprompter. Tap shows/hides the controls (they hide by themselves
 * while playing), double tap plays/pauses, drag scrolls by hand, pinch resizes the text.
 */
@Composable
fun TeleprompterScreen(
    onBack: () -> Unit,
    viewModel: TeleprompterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompter = rememberPrompterState()
    val scope = rememberCoroutineScope()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var countingDown by rememberSaveable { mutableStateOf(false) }

    KeepScreenOn()
    ImmersiveMode()

    // Controls fade away a few seconds after reading starts.
    LaunchedEffect(prompter.playing, controlsVisible) {
        if (prompter.playing && controlsVisible) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    val togglePlay = {
        when {
            prompter.playing -> prompter.playing = false
            countingDown -> countingDown = false
            state.settings.countdownSeconds > 0 -> countingDown = true
            else -> prompter.playing = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("teleprompter_screen"),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GlassTheme.colors.accent)
            state.notFound -> GlassEmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.editor_not_found_title),
                message = stringResource(R.string.editor_not_found_body),
                actionText = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
            state.text.isBlank() -> GlassEmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.prompter_empty_title),
                message = stringResource(R.string.prompter_empty_body),
                actionText = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Prompter(
                text = state.text,
                wordCount = state.wordCount,
                settings = state.settings,
                state = prompter,
                onTogglePlay = togglePlay,
                onFontSizeChange = viewModel::onFontSizeChange,
                onTap = { controlsVisible = !controlsVisible },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !state.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopControls(
                    title = state.projectName,
                    mirror = state.settings.mirror,
                    onBack = onBack,
                    onMirror = viewModel::onMirrorToggle,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (!state.notFound && state.text.isNotBlank()) {
                    BottomControls(
                        state = state,
                        prompter = prompter,
                        playing = prompter.playing || countingDown,
                        onTogglePlay = togglePlay,
                        onRestart = { scope.launch { countingDown = false; prompter.restart() } },
                        onSpeedStep = viewModel::onSpeedStep,
                        onFontStep = viewModel::onFontStep,
                        onPosition = viewModel::onPositionSelected,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        CountdownOverlay(
            active = countingDown,
            seconds = state.settings.countdownSeconds,
            onFinished = {
                countingDown = false
                prompter.playing = true
            },
        )
    }
}

@Composable
private fun TopControls(title: String, mirror: Boolean, onBack: () -> Unit, onMirror: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(GlassTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
    ) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        GlassIconButton(Icons.Outlined.Flip, stringResource(R.string.setup_mirror), onMirror, selected = mirror)
    }
}

@Composable
private fun BottomControls(
    state: TeleprompterUiState,
    prompter: PrompterState,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onSpeedStep: (Boolean) -> Unit,
    onFontStep: (Boolean) -> Unit,
    onPosition: (TextPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val remaining = ((1f - prompter.progress) * readingSeconds(state.wordCount, state.settings.speed)).toLong()
    GlassPanel(
        modifier.widthIn(max = 640.dp).fillMaxWidth().padding(spacing.sm),
        shape = GlassTheme.shapes.extraLarge,
        contentPadding = PaddingValues(spacing.md),
    ) {
        LinearProgressIndicator(
            progress = { prompter.progress },
            color = GlassTheme.colors.accent,
            trackColor = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.prompter_remaining, durationLabel(remaining)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.xs),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SpeedAndSizeControls(state.settings, onSpeedStep, onFontStep)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    GlassIconButton(Icons.Outlined.Replay, stringResource(R.string.prompter_restart), onRestart, size = 40.dp)
                    TextPosition.entries.forEach { position ->
                        GlassChip(
                            text = stringResource(position.labelRes()),
                            selected = state.settings.position == position,
                            onClick = { onPosition(position) },
                        )
                    }
                }
            }
            GlassFloatingButton(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.prompter_pause else R.string.prompter_play),
                onClick = onTogglePlay,
                modifier = Modifier.testTag("prompter_play"),
            )
        }
    }
}

private fun readingSeconds(words: Int, speed: Float): Float =
    words / (ScriptStats.WORDS_PER_MINUTE * speed) * 60f

internal fun TextPosition.labelRes(): Int = when (this) {
    TextPosition.TOP -> R.string.prompter_position_top
    TextPosition.CENTER -> R.string.prompter_position_center
    TextPosition.BOTTOM -> R.string.prompter_position_bottom
}

private const val CONTROLS_HIDE_MS = 3_000L
