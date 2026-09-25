package com.arnoldcode.glassprompt.feature.projectsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.CameraRear
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSlider
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSnackbarHost
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSwitchRow
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTextField
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.VideoOrientation
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import kotlin.math.roundToInt

@Composable
fun ProjectSetupScreen(
    onBack: () -> Unit,
    onCreated: (projectId: String) -> Unit,
    onUpdated: () -> Unit,
    viewModel: ProjectSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        when (val result = state.result) {
            is SetupResult.Created -> onCreated(result.projectId)
            SetupResult.Updated -> onUpdated()
            null -> return@LaunchedEffect
        }
        viewModel.onResultHandled()
    }
    ProjectSetupContent(
        state = state,
        actions = ProjectSetupActions(
            onBack = onBack,
            onNameChange = viewModel::onNameChange,
            onLensSelected = viewModel::onLensSelected,
            onOrientationSelected = viewModel::onOrientationSelected,
            onResolutionSelected = viewModel::onResolutionSelected,
            onFrameRateSelected = viewModel::onFrameRateSelected,
            onSpeedChange = viewModel::onSpeedChange,
            onFontSizeChange = viewModel::onFontSizeChange,
            onMirrorChange = viewModel::onMirrorChange,
            onSave = viewModel::onSave,
            onErrorShown = viewModel::onErrorShown,
        ),
    )
}

internal data class ProjectSetupActions(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onLensSelected: (CameraLens) -> Unit = {},
    val onOrientationSelected: (VideoOrientation) -> Unit = {},
    val onResolutionSelected: (VideoResolution) -> Unit = {},
    val onFrameRateSelected: (Int) -> Unit = {},
    val onSpeedChange: (Float) -> Unit = {},
    val onFontSizeChange: (Float) -> Unit = {},
    val onMirrorChange: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

@Composable
internal fun ProjectSetupContent(state: ProjectSetupUiState, actions: ProjectSetupActions, modifier: Modifier = Modifier) {
    val spacing = GlassTheme.spacing
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.errorMessage?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            actions.onErrorShown()
        }
    }

    Box(modifier.fillMaxSize().testTag("project_setup_screen")) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = stringResource(if (state.isEditing) R.string.setup_title_edit else R.string.setup_title_new),
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), actions.onBack)
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    if (state.templateTitle != null) TemplateBadge(state.templateTitle)
                    GlassTextField(
                        value = state.name,
                        onValueChange = actions.onNameChange,
                        label = stringResource(R.string.setup_name_label),
                        placeholder = stringResource(R.string.setup_name_placeholder),
                        errorText = if (state.showNameError) stringResource(R.string.setup_name_error) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("setup_name"),
                    )
                    RecordingPanel(state.recording, actions)
                    TeleprompterPanel(state.teleprompter, actions)
                }
            }
            GlassButton(
                text = stringResource(if (state.isEditing) R.string.setup_save else R.string.setup_create),
                icon = if (state.isEditing) Icons.Outlined.Check else Icons.Outlined.Edit,
                onClick = actions.onSave,
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = spacing.lg, vertical = spacing.md)
                    .testTag("setup_save"),
            )
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun TemplateBadge(title: String) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.ViewQuilt, contentDescription = null, tint = GlassTheme.colors.accent)
            Text(
                stringResource(R.string.setup_from_template, title),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.textSecondary,
                modifier = Modifier.padding(start = GlassTheme.spacing.sm),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingPanel(recording: RecordingSettings, actions: ProjectSetupActions) {
    SetupSection(stringResource(R.string.setup_section_recording)) {
        OptionLabel(stringResource(R.string.setup_camera))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            GlassChip(stringResource(R.string.setup_camera_front), recording.lens == CameraLens.FRONT, { actions.onLensSelected(CameraLens.FRONT) }, leadingIcon = Icons.Outlined.CameraFront)
            GlassChip(stringResource(R.string.setup_camera_back), recording.lens == CameraLens.BACK, { actions.onLensSelected(CameraLens.BACK) }, leadingIcon = Icons.Outlined.CameraRear)
        }
        OptionLabel(stringResource(R.string.setup_orientation))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            GlassChip(stringResource(R.string.setup_orientation_portrait), recording.orientation == VideoOrientation.PORTRAIT, { actions.onOrientationSelected(VideoOrientation.PORTRAIT) }, leadingIcon = Icons.Outlined.CropPortrait)
            GlassChip(stringResource(R.string.setup_orientation_landscape), recording.orientation == VideoOrientation.LANDSCAPE, { actions.onOrientationSelected(VideoOrientation.LANDSCAPE) }, leadingIcon = Icons.Outlined.CropLandscape)
        }
        OptionLabel(stringResource(R.string.setup_resolution))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            VideoResolution.entries.forEach { resolution ->
                GlassChip(resolution.label(), recording.resolution == resolution, { actions.onResolutionSelected(resolution) })
            }
        }
        OptionLabel(stringResource(R.string.setup_frame_rate))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            RecordingSettings.FrameRates.forEach { fps ->
                GlassChip(stringResource(R.string.setup_fps, fps), recording.frameRate == fps, { actions.onFrameRateSelected(fps) })
            }
        }
        Text(stringResource(R.string.setup_capabilities_note), style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.textTertiary)
    }
}

@Composable
private fun TeleprompterPanel(teleprompter: TeleprompterSettings, actions: ProjectSetupActions) {
    SetupSection(stringResource(R.string.setup_section_teleprompter)) {
        GlassSlider(
            label = stringResource(R.string.setup_speed),
            value = teleprompter.speed,
            onValueChange = actions.onSpeedChange,
            valueText = stringResource(R.string.setup_speed_value, teleprompter.speed),
            valueRange = TeleprompterSettings.SpeedRange,
            startIcon = Icons.Outlined.Speed,
        )
        GlassSlider(
            label = stringResource(R.string.setup_font_size),
            value = teleprompter.fontSizeSp,
            onValueChange = actions.onFontSizeChange,
            valueText = teleprompter.fontSizeSp.roundToInt().toString(),
            valueRange = TeleprompterSettings.FontSizeRange,
            startIcon = Icons.Outlined.FormatSize,
        )
        GlassSwitchRow(
            title = stringResource(R.string.setup_mirror),
            supportingText = stringResource(R.string.setup_mirror_body),
            checked = teleprompter.mirror,
            onCheckedChange = actions.onMirrorChange,
        )
    }
}

@Composable
private fun SetupSection(title: String, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.textSecondary)
}

@Composable
private fun VideoResolution.label(): String = when (this) {
    VideoResolution.HD_720 -> stringResource(R.string.setup_resolution_720)
    VideoResolution.FHD_1080 -> stringResource(R.string.setup_resolution_1080)
    VideoResolution.UHD_2160 -> stringResource(R.string.setup_resolution_4k)
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun ProjectSetupPreview() {
    GlassTheme(darkTheme = true) {
        GlassBackdrop {
            ProjectSetupContent(
                ProjectSetupUiState(isEditing = false, name = "Reseña de cámara", templateTitle = "Reseña de producto"),
                ProjectSetupActions(),
            )
        }
    }
}
