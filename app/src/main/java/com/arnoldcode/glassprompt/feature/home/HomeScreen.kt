package com.arnoldcode.glassprompt.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme

/** Phase 2 placeholder; replaced by the full Home in Phase 3. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTheme.colors.background)
            .safeDrawingPadding()
            .padding(GlassTheme.spacing.lg)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = GlassTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.colors.textSecondary,
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    GlassTheme(darkTheme = true) { HomeScreen() }
}
