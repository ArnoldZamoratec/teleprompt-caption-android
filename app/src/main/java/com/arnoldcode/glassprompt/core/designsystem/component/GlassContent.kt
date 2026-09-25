package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Section title with an optional trailing action ("Ver todo"). Announced as a heading. */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = GlassTheme.colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.accent)
            }
        }
    }
}

/** Friendly empty state: glass icon badge, title, explanation and an optional call to action. */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(GlassTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .glassSurface(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = GlassTheme.colors.accent, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            GlassButton(text = actionText, onClick = onAction, style = GlassButtonStyle.Secondary, modifier = Modifier.padding(top = GlassTheme.spacing.xs))
        }
    }
}
