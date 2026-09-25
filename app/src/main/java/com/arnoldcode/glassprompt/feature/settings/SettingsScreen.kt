package com.arnoldcode.glassprompt.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.BuildConfig
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassButtonStyle
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSwitchRow
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.transcription.SpeechModelStatus
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val speechModel by viewModel.speechModelStatus.collectAsStateWithLifecycle()
    // Back from the system download prompt: the model may have started downloading.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    SettingsContent(
        preferences = preferences,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onReduceEffectsChanged = viewModel::onReduceEffectsChanged,
        onTranscriptionModeSelected = viewModel::onTranscriptionModeSelected,
        onTranscriptionLanguageSelected = viewModel::onTranscriptionLanguageSelected,
        speechModel = speechModel,
        onDownloadSpeechModel = viewModel::onDownloadSpeechModel,
    )
}

@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onReduceEffectsChanged: (Boolean) -> Unit,
    onTranscriptionModeSelected: (TranscriptionMode) -> Unit,
    onTranscriptionLanguageSelected: (String) -> Unit,
    speechModel: SpeechModelStatus?,
    onDownloadSpeechModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("settings_screen"),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = safe.calculateTopPadding() + spacing.md,
                bottom = LocalShellContentPadding.current.calculateBottomPadding() + spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Text(
                    stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineLarge,
                    color = GlassTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item { AppearanceGroup(preferences, onThemeModeSelected, onReduceEffectsChanged) }
            item { CaptionsGroup(preferences, onTranscriptionModeSelected, onTranscriptionLanguageSelected, speechModel, onDownloadSpeechModel) }
            item {
                SettingsGroup(title = stringResource(R.string.settings_privacy)) {
                    Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
                }
            }
            item {
                SettingsGroup(title = stringResource(R.string.settings_about)) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceGroup(
    preferences: UserPreferences,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onReduceEffectsChanged: (Boolean) -> Unit,
) {
    val colors = GlassTheme.colors
    SettingsGroup(title = stringResource(R.string.settings_appearance)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            ThemeMode.entries.forEach { mode ->
                GlassChip(
                    text = stringResource(mode.labelRes()),
                    selected = preferences.themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                    leadingIcon = when (mode) {
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid
                    },
                )
            }
        }
        GlassSwitchRow(
            title = stringResource(R.string.settings_reduce_effects),
            supportingText = stringResource(R.string.settings_reduce_effects_body),
            checked = preferences.reduceEffects,
            onCheckedChange = onReduceEffectsChanged,
        )
    }
}

@Composable
private fun CaptionsGroup(
    preferences: UserPreferences,
    onModeSelected: (TranscriptionMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    speechModel: SpeechModelStatus?,
    onDownloadSpeechModel: () -> Unit,
) {
    val colors = GlassTheme.colors
    SettingsGroup(title = stringResource(R.string.settings_captions)) {
        Text(stringResource(R.string.settings_transcription_mode), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            GlassChip(
                text = stringResource(R.string.settings_transcription_auto),
                selected = preferences.transcriptionMode == TranscriptionMode.AUTO,
                onClick = { onModeSelected(TranscriptionMode.AUTO) },
                leadingIcon = Icons.Outlined.RecordVoiceOver,
            )
            GlassChip(
                text = stringResource(R.string.settings_transcription_script),
                selected = preferences.transcriptionMode == TranscriptionMode.SCRIPT,
                onClick = { onModeSelected(TranscriptionMode.SCRIPT) },
                leadingIcon = Icons.Outlined.Description,
            )
        }
        Text(
            stringResource(
                if (preferences.transcriptionMode == TranscriptionMode.AUTO) R.string.settings_transcription_auto_body else R.string.settings_transcription_script_body,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        if (preferences.transcriptionMode == TranscriptionMode.AUTO) {
            Text(stringResource(R.string.settings_transcription_language), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
            ) {
                SettingsViewModel.TranscriptionLanguages.forEach { tag ->
                    GlassChip(
                        text = if (tag.isEmpty()) stringResource(R.string.settings_language_device) else languageName(tag),
                        selected = preferences.transcriptionLanguage == tag,
                        onClick = { onLanguageSelected(tag) },
                    )
                }
            }
            SpeechModelRow(speechModel, onDownloadSpeechModel)
        }
    }
}

@Composable
private fun SpeechModelRow(status: SpeechModelStatus?, onDownload: () -> Unit) {
    val colors = GlassTheme.colors
    val text = when (status) {
        null -> return
        SpeechModelStatus.INSTALLED -> R.string.settings_speech_model_installed
        SpeechModelStatus.DOWNLOADING -> R.string.settings_speech_model_downloading
        SpeechModelStatus.DOWNLOADABLE -> R.string.settings_speech_model_missing
        SpeechModelStatus.UNSUPPORTED -> R.string.settings_speech_model_unsupported
        SpeechModelStatus.UNAVAILABLE -> R.string.settings_speech_model_unavailable
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = if (status == SpeechModelStatus.INSTALLED) colors.success else colors.warning,
        modifier = Modifier.testTag("speech_model_status"),
    )
    if (status == SpeechModelStatus.DOWNLOADABLE) {
        GlassButton(
            text = stringResource(R.string.settings_speech_model_download),
            onClick = onDownload,
            icon = Icons.Outlined.Download,
            style = GlassButtonStyle.Secondary,
        )
    }
}

/** "Español (España)" in the device language. */
private fun languageName(tag: String): String {
    val display = Locale.getDefault()
    return Locale.forLanguageTag(tag).getDisplayName(display).replaceFirstChar { it.titlecase(display) }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() {
    GlassTheme(darkTheme = true) {
        GlassBackdrop { SettingsContent(UserPreferences(), {}, {}, {}, {}, SpeechModelStatus.INSTALLED, {}) }
    }
}
