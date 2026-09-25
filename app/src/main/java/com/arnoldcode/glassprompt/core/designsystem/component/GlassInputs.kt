package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.glass.glassRim
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.glass.pressScale
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Selectable glass chip (filters, presets, toggles). Selected chips glow with the accent. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = GlassTheme.colors
    val shape = GlassTheme.shapes.pill
    val interactionSource = remember { MutableInteractionSource() }
    val surface = if (selected) {
        Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.85f), colors.accentSecondary.copy(alpha = 0.85f))))
            .glassRim(shape)
    } else {
        Modifier.glassSurface(shape, GlassTheme.elevation.flat)
    }
    val contentColor = if (selected) colors.onAccent else colors.textSecondary
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .pressScale(interactionSource)
            .then(surface)
            .toggleable(
                value = selected,
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = GlassTheme.spacing.md, vertical = GlassTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xxs),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/**
 * Labelled slider on a glass track. [valueText] is shown to the right of the label and read
 * by TalkBack together with the label.
 */
@Composable
fun GlassSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = GlassTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
            if (startIcon != null) Icon(startIcon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = colors.textPrimary,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.glassFillStrong,
                    activeTickColor = colors.onAccent.copy(alpha = 0.6f),
                    inactiveTickColor = colors.textTertiary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label: $valueText" },
            )
            if (endIcon != null) Icon(endIcon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Single- or multi-line glass text field with label, placeholder and inline error. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = GlassTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xxs)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (label != null) contentDescription = label
                    if (errorText != null) error(errorText)
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .glassSurface(
                            GlassTheme.shapes.medium,
                            GlassTheme.elevation.flat,
                            tint = if (errorText != null) colors.error.copy(alpha = 0.18f) else null,
                        )
                        .padding(horizontal = GlassTheme.spacing.md, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                    }
                    inner()
                }
            },
        )
        if (errorText != null) Text(errorText, style = MaterialTheme.typography.bodySmall, color = colors.error)
    }
}
