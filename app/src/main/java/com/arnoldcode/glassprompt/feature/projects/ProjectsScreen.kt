package com.arnoldcode.glassprompt.feature.projects

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.feature.common.TabPlaceholder

/** Project library. Backed by Room in Phase 4. */
@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) {
    TabPlaceholder(
        icon = Icons.Outlined.Folder,
        title = stringResource(R.string.projects_empty_title),
        message = stringResource(R.string.projects_empty_body),
        modifier = modifier.testTag("projects_screen"),
    )
}
