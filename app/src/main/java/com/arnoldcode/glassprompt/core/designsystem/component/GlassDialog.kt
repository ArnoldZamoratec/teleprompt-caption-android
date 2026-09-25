package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arnoldcode.glassprompt.core.designsystem.glass.LocalGlassHazeState
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/**
 * Modal glass dialog. Dialogs live in their own window and cannot sample the screen behind,
 * so the backdrop is cleared and the surface uses its opaque-enough fallback tint.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    message: String? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        CompositionLocalProvider(LocalGlassHazeState provides null) {
            Column(
                modifier = modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .glassSurface(GlassTheme.shapes.extraLarge, GlassTheme.elevation.floating)
                    .padding(GlassTheme.spacing.lg)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.md),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = GlassTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = GlassTheme.colors.textSecondary)
                }
                content?.invoke(this)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.sm, Alignment.End),
                ) {
                    if (dismissText != null) {
                        GlassButton(text = dismissText, onClick = onDismiss, style = GlassButtonStyle.Secondary)
                    }
                    GlassButton(
                        text = confirmText,
                        onClick = onConfirm,
                        style = if (destructive) GlassButtonStyle.Destructive else GlassButtonStyle.Primary,
                    )
                }
            }
        }
    }
}
