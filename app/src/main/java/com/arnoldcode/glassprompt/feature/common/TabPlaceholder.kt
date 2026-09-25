package com.arnoldcode.glassprompt.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding

/** Centered empty state for a top-level tab whose content arrives in a later phase. */
@Composable
internal fun TabPlaceholder(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(LocalShellContentPadding.current),
        contentAlignment = Alignment.Center,
    ) {
        GlassEmptyState(icon = icon, title = title, message = message)
    }
}
