package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.glass.glassRim
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.glass.liquidGlass
import com.arnoldcode.glassprompt.core.designsystem.glass.pressScale
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

enum class GlassButtonStyle { Primary, Secondary, Destructive }

/**
 * Pill button. [GlassButtonStyle.Primary] is a luminous blue→violet gradient for the main
 * action on a screen; [GlassButtonStyle.Secondary] is clear glass; [GlassButtonStyle.Destructive]
 * is a red gradient reserved for irreversible actions.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = GlassTheme.colors
    val shape = GlassTheme.shapes.pill
    val interactionSource = remember { MutableInteractionSource() }
    val surface = when (style) {
        GlassButtonStyle.Primary -> Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.accent, colors.accentSecondary)))
            .glassRim(shape)
        GlassButtonStyle.Destructive -> Modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.error, colors.recording)))
            .glassRim(shape)
        GlassButtonStyle.Secondary -> Modifier.glassSurface(shape)
    }
    val contentColor = if (style == GlassButtonStyle.Secondary) colors.textPrimary else colors.onAccent

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(interactionSource, enabled)
            .then(surface)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = GlassTheme.spacing.lg, vertical = GlassTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Circular glass icon button with a guaranteed 48dp touch target. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color = GlassTheme.colors.textPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = GlassTheme.colors
    Box(
        modifier = modifier
            .size(maxOf(size, GlassTheme.spacing.minTouchTarget))
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(interactionSource, enabled)
            .glassSurface(CircleShape, GlassTheme.elevation.flat, tint = if (selected) colors.accent.copy(alpha = 0.35f) else null)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = tint),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.onAccent else tint, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * Hero action (record, create). Refractive liquid glass with an accent tint and a
 * confirmation haptic.
 */
@Composable
fun GlassFloatingButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    tint: Color = GlassTheme.colors.accent,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interactionSource)
            .liquidGlass(CircleShape, tint = tint.copy(alpha = 0.82f), interactionSource = interactionSource)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = GlassTheme.colors.onAccent),
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = GlassTheme.colors.onAccent, modifier = Modifier.size(size * 0.42f))
    }
}
