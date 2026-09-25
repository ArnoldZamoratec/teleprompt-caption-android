package com.arnoldcode.glassprompt.feature.camera

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSlider
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.permissions.PermissionStatus
import com.arnoldcode.glassprompt.core.permissions.rememberPermissionsState
import com.arnoldcode.glassprompt.domain.model.RecordingState
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.feature.teleprompter.ImmersiveMode
import com.arnoldcode.glassprompt.feature.teleprompter.KeepScreenOn
import com.arnoldcode.glassprompt.feature.teleprompter.LockOrientation
import com.arnoldcode.glassprompt.feature.teleprompter.Prompter
import com.arnoldcode.glassprompt.feature.teleprompter.SpeedAndSizeControls
import com.arnoldcode.glassprompt.feature.teleprompter.rememberPrompterState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val CameraPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
fun CameraScreen(
    onExit: () -> Unit,
    onTakeSaved: (takeId: String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionsState(CameraPermissions)

    KeepScreenOn()
    ImmersiveMode()
    if (!state.isLoading) LockOrientation(landscape = state.isLandscape)
    BackHandler(onBack = viewModel::onBackRequest)

    LaunchedEffect(state.exit) { if (state.exit) onExit() }
    LaunchedEffect(state.savedTakeId) {
        state.savedTakeId?.let {
            viewModel.onSavedTakeHandled()
            onTakeSaved(it)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_screen")) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GlassTheme.colors.accent)
            state.notFound -> GlassEmptyState(
                icon = Icons.Outlined.NoPhotography,
                title = stringResource(R.string.editor_not_found_title),
                message = stringResource(R.string.editor_not_found_body),
                actionText = stringResource(R.string.action_back),
                onAction = onExit,
                modifier = Modifier.align(Alignment.Center),
            )
            !permissions.allGranted -> PermissionGate(
                status = permissions.status,
                onRequest = permissions::launchRequest,
                onOpenSettings = permissions::openAppSettings,
                onBack = onExit,
            )
            else -> CameraContent(state, viewModel)
        }
    }
}

@Composable
private fun PermissionGate(status: PermissionStatus, onRequest: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(GlassTheme.spacing.lg), contentAlignment = Alignment.Center) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack, modifier = Modifier.align(Alignment.TopStart))
        GlassPanel(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = GlassTheme.shapes.extraLarge, contentPadding = PaddingValues(GlassTheme.spacing.lg)) {
            GlassEmptyState(
                icon = Icons.Outlined.Videocam,
                title = stringResource(R.string.camera_permission_title),
                message = stringResource(
                    if (status == PermissionStatus.PermanentlyDenied) R.string.camera_permission_blocked else R.string.camera_permission_body,
                ),
            )
            if (status == PermissionStatus.PermanentlyDenied) {
                GlassButton(stringResource(R.string.camera_permission_settings), onOpenSettings, modifier = Modifier.fillMaxWidth())
            } else {
                GlassButton(stringResource(R.string.camera_permission_allow), onRequest, modifier = Modifier.fillMaxWidth().testTag("camera_permission_allow"))
            }
        }
    }
}

@Composable
private fun CameraContent(state: CameraUiState, viewModel: CameraViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val zoom by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val prompter = rememberPrompterState()
    val snackbar = remember { SnackbarHostState() }
    var chromeVisible by remember { mutableStateOf(true) }
    var exposureOpen by remember { mutableStateOf(false) }

    // (Re)bind whenever the lens changes; CameraX unbinds by itself when the screen stops.
    LaunchedEffect(state.lens, state.bindAttempt, lifecycleOwner) { viewModel.bindCamera(lifecycleOwner) }
    LaunchedEffect(viewModel) {
        viewModel.prompterCommands.collect { command ->
            prompter.playing = command == PrompterCommand.Play
        }
    }
    // While recording the UI steps back after a few seconds; a tap brings it back.
    val recording = state.recording is RecordingState.Recording
    LaunchedEffect(recording, chromeVisible) {
        if (recording && chromeVisible) {
            delay(3_000)
            chromeVisible = false
        }
    }
    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Viewfinder(
            surfaceRequest = surfaceRequest,
            onTap = { chromeVisible = true },
            onFocus = viewModel::onFocus,
            onZoomBy = viewModel::onZoomBy,
        )
        if (state.cameraError) {
            GlassEmptyState(
                icon = Icons.Outlined.NoPhotography,
                title = stringResource(R.string.camera_error_title),
                message = stringResource(R.string.error_camera),
                actionText = stringResource(R.string.action_retry),
                onAction = viewModel::onRetryCamera,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Prompter as close to the lens as possible: top of the screen (left half in landscape).
        val prompterShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .then(if (landscape) Modifier.fillMaxHeight().fillMaxWidth(0.55f) else Modifier.fillMaxWidth().fillMaxHeight(0.40f))
                .clip(prompterShape)
                .background(Color.Black.copy(alpha = state.teleprompter.backgroundOpacity)),
        ) {
            if (state.script.isNotBlank()) {
                Prompter(
                    text = state.script,
                    wordCount = state.wordCount,
                    settings = state.teleprompter,
                    state = prompter,
                    onTogglePlay = { prompter.playing = !prompter.playing },
                    onFontSizeChange = viewModel::onFontSizeChange,
                    onTap = { chromeVisible = !chromeVisible },
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                )
            } else {
                Text(
                    stringResource(R.string.prompter_empty_body),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(GlassTheme.spacing.lg),
                )
            }
        }

        // While recording only REC, pause/stop and the prompter toggle stay; the rest fades out.
        val showChrome = chromeVisible || !recording
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedVisibility(visible = showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                TopChrome(state, onBack = viewModel::onBackRequest, onTorch = viewModel::onTorchToggle, onSwitch = viewModel::onSwitchLens)
            }
            Column(
                Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter).padding(GlassTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm),
            ) {
                AnimatedVisibility(visible = showChrome, enter = fadeIn(), exit = fadeOut()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm),
                    ) {
                        if (exposureOpen) ExposurePanel(state, viewModel::onExposureChange)
                        ZoomAndExposureRow(state, zoom, onZoomTo = viewModel::onZoomTo, onExposure = { exposureOpen = !exposureOpen }, exposureOpen = exposureOpen)
                        SpeedAndSizeControls(state.teleprompter, viewModel::onSpeedStep, viewModel::onFontStep)
                    }
                }
                RecordControls(
                    state = state,
                    prompterPlaying = prompter.playing,
                    onTogglePrompter = { prompter.playing = !prompter.playing },
                    onRecord = viewModel::onRecordClick,
                    onPauseResume = viewModel::onPauseResumeClick,
                )
            }
        }

        RecIndicator(state.recording, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(GlassTheme.spacing.md))
        CountdownNumber(state.recording, Modifier.align(Alignment.Center))
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }

    if (state.confirmDiscard) {
        GlassDialog(
            title = stringResource(R.string.camera_discard_title),
            message = stringResource(R.string.camera_discard_body),
            onDismissRequest = viewModel::onDiscardDismiss,
            confirmText = stringResource(R.string.camera_discard_confirm),
            onConfirm = viewModel::onDiscardConfirm,
            dismissText = stringResource(R.string.camera_discard_keep),
            destructive = true,
        )
    }
}

@Composable
private fun Viewfinder(
    surfaceRequest: androidx.camera.core.SurfaceRequest?,
    onTap: () -> Unit,
    onFocus: (Float, Float) -> Unit,
    onZoomBy: (Float) -> Unit,
) {
    val transformer = remember { MutableCoordinateTransformer() }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1_200)
            focusPoint = null
        }
    }
    val request = surfaceRequest ?: return
    val focusDescription = stringResource(R.string.camera_focus_hint)
    Box(Modifier.fillMaxSize()) {
        CameraXViewfinder(
            surfaceRequest = request,
            coordinateTransformer = transformer,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = focusDescription }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        onTap()
                        focusPoint = tap
                        with(transformer) {
                            val surface = tap.transform()
                            onFocus(surface.x, surface.y)
                        }
                    }
                }
                .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onZoomBy(zoom) } },
        )
        focusPoint?.let { point ->
            val half = with(LocalDensity.current) { 32.dp.roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(point.x.roundToInt() - half, point.y.roundToInt() - half) }
                    .size(64.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}

@Composable
private fun TopChrome(state: CameraUiState, onBack: () -> Unit, onTorch: () -> Unit, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    val caps = state.bound?.capabilities
    Row(
        modifier.fillMaxWidth().padding(GlassTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
    ) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
        Spacer(Modifier.weight(1f))
        state.bound?.let { bound ->
            Text(
                stringResource(R.string.camera_quality, bound.resolution.shortLabel(), bound.frameRate),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.clip(GlassTheme.shapes.pill).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (caps?.hasTorch == true) {
            GlassIconButton(
                if (state.torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                stringResource(if (state.torchOn) R.string.camera_torch_off else R.string.camera_torch_on),
                onTorch,
                selected = state.torchOn,
            )
        }
        if ((caps?.availableLenses?.size ?: 0) > 1) {
            GlassIconButton(Icons.Outlined.Cameraswitch, stringResource(R.string.camera_switch), onSwitch, enabled = !state.isBusy)
        }
    }
}

@Composable
private fun ZoomAndExposureRow(state: CameraUiState, zoom: Float, onZoomTo: (Float) -> Unit, onExposure: () -> Unit, exposureOpen: Boolean) {
    val caps = state.bound?.capabilities ?: return
    val presets = buildList {
        if (caps.minZoom < 1f) add(caps.minZoom)
        add(1f)
        if (caps.maxZoom >= 2f) add(2f)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        presets.forEach { preset ->
            GlassChip(
                text = zoomLabel(preset),
                selected = kotlin.math.abs(zoom - preset) < 0.05f,
                onClick = { onZoomTo(preset) },
            )
        }
        if (caps.exposureRange.first != caps.exposureRange.last) {
            GlassIconButton(Icons.Outlined.Exposure, stringResource(R.string.camera_exposure), onExposure, size = 40.dp, selected = exposureOpen)
        }
    }
}

@Composable
private fun ExposurePanel(state: CameraUiState, onChange: (Int) -> Unit) {
    val caps = state.bound?.capabilities ?: return
    GlassPanel(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
        GlassSlider(
            label = stringResource(R.string.camera_exposure),
            value = state.exposureIndex.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueText = String.format(Locale.ROOT, "%+.1f EV", state.exposureIndex * caps.exposureStep),
            valueRange = caps.exposureRange.first.toFloat()..caps.exposureRange.last.toFloat(),
            steps = (caps.exposureRange.last - caps.exposureRange.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun RecordControls(
    state: CameraUiState,
    prompterPlaying: Boolean,
    onTogglePrompter: () -> Unit,
    onRecord: () -> Unit,
    onPauseResume: () -> Unit,
) {
    val recordingState = state.recording
    val active = recordingState is RecordingState.Recording || recordingState is RecordingState.Paused
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.lg)) {
        GlassIconButton(
            if (prompterPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (prompterPlaying) R.string.prompter_pause else R.string.prompter_play),
            onTogglePrompter,
            size = 52.dp,
        )
        RecordButton(recordingState, onRecord, enabled = state.bound != null && recordingState != RecordingState.Finalizing)
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (active) {
                GlassIconButton(
                    if (recordingState is RecordingState.Paused) Icons.Outlined.Videocam else Icons.Filled.Pause,
                    stringResource(if (recordingState is RecordingState.Paused) R.string.camera_resume else R.string.camera_pause),
                    onPauseResume,
                    size = 52.dp,
                    modifier = Modifier.testTag("camera_pause"),
                )
            }
        }
    }
}

/** Classic record control: red dot when idle, rounded square while recording. */
@Composable
private fun RecordButton(recording: RecordingState, onClick: () -> Unit, enabled: Boolean) {
    val active = recording is RecordingState.Recording || recording is RecordingState.Paused
    val corner by animateFloatAsState(if (active) 0.28f else 0.5f, label = "recordShape")
    val inner by animateFloatAsState(if (active) 0.42f else 0.78f, label = "recordSize")
    val label = stringResource(
        when {
            active -> R.string.camera_stop
            recording is RecordingState.Countdown -> R.string.camera_cancel_countdown
            else -> R.string.camera_record
        },
    )
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .testTag("camera_record"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(80.dp * inner)
                .clip(RoundedCornerShape(percent = (corner * 100).roundToInt()))
                .background(GlassTheme.colors.recording.copy(alpha = if (enabled) 1f else 0.4f)),
        )
    }
}

@Composable
private fun RecIndicator(recording: RecordingState, modifier: Modifier = Modifier) {
    val elapsed = when (recording) {
        is RecordingState.Recording -> recording.elapsedMs
        is RecordingState.Paused -> recording.elapsedMs
        else -> return
    }
    val paused = recording is RecordingState.Paused
    val seconds = elapsed / 1000
    val time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    Row(
        modifier
            .clip(GlassTheme.shapes.pill)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("camera_rec_indicator"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) Color.White else GlassTheme.colors.recording))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (paused) R.string.camera_paused_label else R.string.camera_rec_label, time),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CountdownNumber(recording: RecordingState, modifier: Modifier = Modifier) {
    val countdown = recording as? RecordingState.Countdown ?: return
    Text(
        countdown.secondsLeft.toString(),
        fontSize = 120.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

private fun zoomLabel(ratio: Float): String =
    if (ratio < 1f) String.format(Locale.ROOT, "%.1fx", ratio) else String.format(Locale.ROOT, "%.0fx", ratio)

@Composable
private fun VideoResolution.shortLabel(): String = when (this) {
    VideoResolution.HD_720 -> stringResource(R.string.setup_resolution_720)
    VideoResolution.FHD_1080 -> stringResource(R.string.setup_resolution_1080)
    VideoResolution.UHD_2160 -> stringResource(R.string.setup_resolution_4k)
}
