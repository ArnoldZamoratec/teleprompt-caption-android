package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.glass.pressScale
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassDepth
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/**
 * Interactive or static glass card. When [onClick] is set the whole card is one accessible
 * button (use [onClickLabel] to describe the action for TalkBack).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = GlassTheme.shapes.large,
    depth: GlassDepth = GlassTheme.elevation.raised,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(GlassTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .pressScale(interactionSource)
            .glassSurface(shape, depth, tint)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = GlassTheme.colors.textPrimary),
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier.glassSurface(shape, depth, tint)
    }
    Column(
        modifier = modifier
            .then(clickModifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Flat, non-interactive glass container for grouping controls (settings groups, toolbars). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(GlassTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glassSurface(shape, GlassTheme.elevation.flat)
            .padding(contentPadding),
        content = content,
    )
}
