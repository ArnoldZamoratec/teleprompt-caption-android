package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.glass.liquidGlass
import com.arnoldcode.glassprompt.core.designsystem.glass.pressScale
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Glass app bar that extends under the status bar. */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(RectangleShape, GlassTheme.elevation.flat)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = GlassTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
    ) {
        if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(GlassTheme.spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = GlassTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

/** One destination in [GlassBottomBar] / [GlassNavigationRail]. */
data class GlassNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * Floating liquid-glass pill with the app's tabs and an optional centre [centerAction]
 * (the record button). Sits above the navigation bar inset.
 */
@Composable
fun GlassBottomBar(
    items: List<GlassNavItem>,
    selectedKey: String,
    onSelect: (GlassNavItem) -> Unit,
    modifier: Modifier = Modifier,
    centerAction: (@Composable () -> Unit)? = null,
) {
    val half = if (centerAction != null) items.size / 2 else items.size
    // Refraction alone washes out to milky grey over a dark scene; a tint keeps labels legible.
    val colors = GlassTheme.colors
    val barTint = colors.backgroundElevated.copy(alpha = if (colors.isDark) 0.62f else 0.35f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = GlassTheme.spacing.md, vertical = GlassTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .liquidGlass(GlassTheme.shapes.pill, tint = barTint)
                .selectableGroup()
                .padding(horizontal = GlassTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index == half && centerAction != null) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { centerAction() }
                }
                GlassNavDestination(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Vertical variant of [GlassBottomBar] for tablets and landscape (≥600dp). */
@Composable
fun GlassNavigationRail(
    items: List<GlassNavItem>,
    selectedKey: String,
    onSelect: (GlassNavItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(GlassTheme.spacing.sm)
            .width(88.dp)
            .glassSurface(GlassTheme.shapes.extraLarge)
            .selectableGroup()
            .padding(vertical = GlassTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm),
    ) {
        header?.invoke()
        items.forEach { item ->
            GlassNavDestination(item = item, selected = item.key == selectedKey, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun GlassNavDestination(
    item: GlassNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        label = "navColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = 0.28f) else colors.accent.copy(alpha = 0f),
        label = "navIndicator",
    )
    Column(
        modifier = modifier
            .pressScale(interactionSource)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(vertical = GlassTheme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(indicatorColor, GlassTheme.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
