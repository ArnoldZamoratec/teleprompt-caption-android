package com.arnoldcode.glassprompt.feature.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassCard
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassDialog
import com.arnoldcode.glassprompt.core.designsystem.component.GlassPanel
import com.arnoldcode.glassprompt.core.designsystem.glass.GlassBackdrop
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.core.navigation.LocalShellContentPadding
import com.arnoldcode.glassprompt.domain.model.ScriptTemplate
import com.arnoldcode.glassprompt.domain.model.TemplateCategory

@Composable
fun TemplatesScreen(
    onUseTemplate: (templateId: String) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TemplatesContent(
        state = state,
        onCategorySelected = viewModel::onCategorySelected,
        onPreview = viewModel::onPreview,
        onPreviewDismiss = viewModel::onPreviewDismiss,
        onUseTemplate = { id ->
            viewModel.onPreviewDismiss()
            onUseTemplate(id)
        },
    )
}

@Composable
internal fun TemplatesContent(
    state: TemplatesUiState,
    onCategorySelected: (TemplateCategory?) -> Unit,
    onPreview: (ScriptTemplate) -> Unit,
    onPreviewDismiss: () -> Unit,
    onUseTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    Box(modifier.fillMaxSize().testTag("templates_screen"), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = safe.calculateTopPadding() + spacing.md,
                bottom = LocalShellContentPadding.current.calculateBottomPadding() + spacing.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        stringResource(R.string.nav_templates),
                        style = MaterialTheme.typography.headlineLarge,
                        color = GlassTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.templates_subtitle), style = MaterialTheme.typography.bodyLarge, color = GlassTheme.colors.textSecondary)
                }
            }
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    modifier = Modifier.bleedHorizontally(spacing.lg),
                    contentPadding = PaddingValues(horizontal = spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    item { GlassChip(stringResource(R.string.templates_all), state.category == null, { onCategorySelected(null) }) }
                    items(state.categories) { category ->
                        GlassChip(stringResource(category.labelRes()), state.category == category, { onCategorySelected(category) }, leadingIcon = category.icon())
                    }
                }
            }
            items(state.visible, key = { it.id }) { template -> TemplateCard(template, onPreview) }
        }
    }

    state.preview?.let { template ->
        GlassDialog(
            title = template.title,
            message = template.description,
            onDismissRequest = onPreviewDismiss,
            confirmText = stringResource(R.string.templates_use),
            onConfirm = { onUseTemplate(template.id) },
            dismissText = stringResource(R.string.action_close),
        ) {
            GlassPanel(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                Text(
                    template.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.textPrimary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(template: ScriptTemplate, onPreview: (ScriptTemplate) -> Unit) {
    val colors = GlassTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("template_${template.id}"),
        onClick = { onPreview(template) },
        onClickLabel = stringResource(R.string.templates_preview),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(template.category.icon(), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(GlassTheme.spacing.xs))
            Text(stringResource(template.category.labelRes()), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Spacer(Modifier.height(GlassTheme.spacing.sm))
        Text(template.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(template.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Lets a horizontally scrolling row extend [bleed] past its parent's padding on both sides. */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

private fun TemplateCategory.labelRes(): Int = when (this) {
    TemplateCategory.TUTORIAL -> R.string.template_category_tutorial
    TemplateCategory.REVIEW -> R.string.template_category_review
    TemplateCategory.AD -> R.string.template_category_ad
    TemplateCategory.INTRO -> R.string.template_category_intro
    TemplateCategory.STORY -> R.string.template_category_story
    TemplateCategory.EDUCATIONAL -> R.string.template_category_educational
}

private fun TemplateCategory.icon(): ImageVector = when (this) {
    TemplateCategory.TUTORIAL -> Icons.Outlined.School
    TemplateCategory.REVIEW -> Icons.Outlined.RateReview
    TemplateCategory.AD -> Icons.Outlined.Campaign
    TemplateCategory.INTRO -> Icons.Outlined.WavingHand
    TemplateCategory.STORY -> Icons.Outlined.AutoStories
    TemplateCategory.EDUCATIONAL -> Icons.Outlined.Lightbulb
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TemplatesPreview() {
    val templates = listOf(
        ScriptTemplate("a", TemplateCategory.TUTORIAL, "Tutorial paso a paso", "Enseña a hacer algo en pasos claros.", "Hola…"),
        ScriptTemplate("b", TemplateCategory.REVIEW, "Reseña de producto", "Opinión honesta con pros y contras.", "¿Vale la pena…"),
    )
    GlassTheme(darkTheme = true) {
        GlassBackdrop { TemplatesContent(TemplatesUiState(all = templates), {}, {}, {}, {}) }
    }
}
