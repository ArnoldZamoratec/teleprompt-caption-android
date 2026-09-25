package com.arnoldcode.glassprompt.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassEmptyState
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.component.GlassTopBar

/** Temporary destination for flows scheduled in later phases. */
@Composable
fun ComingSoonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = stringResource(R.string.coming_soon_title),
            navigationIcon = {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            GlassEmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.coming_soon_title),
                message = stringResource(R.string.coming_soon_message),
            )
        }
    }
}
