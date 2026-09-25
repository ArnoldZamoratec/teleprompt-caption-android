package com.arnoldcode.glassprompt.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Snackbar host whose messages render as floating glass pills, announced politely by TalkBack. */
@Composable
fun GlassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Row(
            modifier = Modifier
                .padding(GlassTheme.spacing.md)
                .widthIn(max = 560.dp)
                .glassSurface(GlassTheme.shapes.pill, GlassTheme.elevation.floating)
                .padding(horizontal = GlassTheme.spacing.lg, vertical = GlassTheme.spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textPrimary)
        }
    }
}
