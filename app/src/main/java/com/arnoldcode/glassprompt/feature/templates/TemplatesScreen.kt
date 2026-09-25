package com.arnoldcode.glassprompt.feature.templates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.feature.common.TabPlaceholder

/** Script templates. Content arrives with the script editor in Phase 4. */
@Composable
fun TemplatesScreen(modifier: Modifier = Modifier) {
    TabPlaceholder(
        icon = Icons.AutoMirrored.Outlined.ViewQuilt,
        title = stringResource(R.string.templates_empty_title),
        message = stringResource(R.string.templates_empty_body),
        modifier = modifier.testTag("templates_screen"),
    )
}
