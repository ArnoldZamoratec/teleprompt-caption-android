package com.arnoldcode.glassprompt.feature.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSwitchRow
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.data.work.ExportNotifications
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.VideoResolution

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    onExported: (exportId: String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.finishedExportId) {
        state.finishedExportId?.let { id ->
            viewModel.onFinishedShown()
            onExported(id)
        }
    }
    // The progress notification is optional: exporting goes ahead whatever the answer.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onStart()
    }
    val start = {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.onStart()
    }

    Column(Modifier.fillMaxSize().testTag("export_screen")) {
        GlassTopBar(
            title = stringResource(R.string.export_title),
            navigationIcon = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack) },
        )
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = GlassTheme.colors.accent)
            }
            state.take == null || state.settings == null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                GlassEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.review_missing_title),
                    message = stringResource(R.string.review_missing_body),
                    actionText = stringResource(R.string.action_back),
                    onAction = onBack,
                )
            }
            else -> ExportContent(state, viewModel, start, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExportContent(state: ExportUiState, viewModel: ExportViewModel, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GlassTheme.spacing
    val colors = GlassTheme.colors
    val settings = checkNotNull(state.settings)
    val context = LocalContext.current
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
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Label(stringResource(R.string.export_resolution))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.resolutions.forEach { resolution ->
                            GlassChip(
                                text = resolution.label(),
                                selected = settings.resolution == resolution,
                                onClick = { viewModel.onResolution(resolution) },
                                modifier = Modifier.testTag("export_resolution_${resolution.height}"),
                            )
                        }
                    }
                    Label(stringResource(R.string.export_frame_rate))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.frameRates.forEach { fps ->
                            GlassChip(stringResource(R.string.export_fps, fps), settings.frameRate == fps, { viewModel.onFrameRate(fps) })
                        }
                    }
                    GlassSwitchRow(
                        title = stringResource(R.string.export_captions),
                        supportingText = stringResource(if (state.hasCaptions) R.string.export_captions_body else R.string.export_captions_none),
                        checked = settings.burnCaptions,
                        onCheckedChange = viewModel::onCaptions,
                    )
                    val (width, height) = checkNotNull(state.outputSize)
                    Text(
                        stringResource(R.string.export_estimate, width, height, Formatter.formatShortFileSize(context, state.estimatedBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Text(stringResource(R.string.export_honest_limits), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }

            when (val export = state.export) {
                is ExportState.Running -> GlassPanel(Modifier.fillMaxWidth().testTag("export_progress").semantics { liveRegion = LiveRegionMode.Polite }) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            stringResource(R.string.export_progress, (export.progress * 100).toInt()),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        LinearProgressIndicator(
                            progress = { export.progress },
                            color = colors.accent,
                            trackColor = colors.glassFillStrong,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            export.remainingMs?.let { stringResource(R.string.export_remaining, ExportNotifications.formatRemaining(it)) }
                                ?: stringResource(R.string.export_estimating),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Text(stringResource(R.string.export_running_body), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                    }
                }
                is ExportState.Failed -> GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.warning)
                        Text(stringResource(export.reason.labelRes()), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    }
                }
                else -> Unit
            }

            if (state.isExporting) {
                GlassButton(
                    text = stringResource(R.string.export_cancel),
                    icon = Icons.Outlined.Close,
                    onClick = viewModel::onCancel,
                    style = GlassButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GlassButton(
                    text = stringResource(R.string.export_start),
                    icon = Icons.Outlined.IosShare,
                    onClick = {
                        if (state.export is ExportState.Failed) viewModel.onErrorShown()
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_start"),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.textSecondary)
}

internal fun VideoResolution.label(): String = when (this) {
    VideoResolution.HD_720 -> "720p"
    VideoResolution.FHD_1080 -> "1080p"
    VideoResolution.UHD_2160 -> "4K"
}

private fun ExportState.Reason.labelRes(): Int = when (this) {
    ExportState.Reason.NOT_FOUND -> R.string.export_failed_not_found
    ExportState.Reason.STORAGE_FULL -> R.string.export_failed_storage
    ExportState.Reason.ENCODER -> R.string.export_failed_encoder
    ExportState.Reason.UNKNOWN -> R.string.export_failed_unknown
}
