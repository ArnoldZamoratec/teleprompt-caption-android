package com.arnoldcode.glassprompt.feature.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.BuildConfig
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSwitchRow
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    SettingsContent(
        preferences = preferences,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onReduceEffectsChanged = viewModel::onReduceEffectsChanged,
    )
}

@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onReduceEffectsChanged: (Boolean) -> Unit,
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
        GlassBackdrop { SettingsContent(UserPreferences(), {}, {}) }
    }
}
