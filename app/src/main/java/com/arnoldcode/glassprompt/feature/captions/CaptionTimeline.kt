package com.arnoldcode.glassprompt.feature.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.captions.CaptionMotion
import com.arnoldcode.glassprompt.domain.model.Caption
import java.util.Locale

/**
 * Horizontal timeline: one block per caption, as wide as it lasts, with gaps where nobody
 * speaks. Tapping a block selects it and seeks there; while playing it follows the playhead.
 */
@Composable
fun CaptionTimeline(
    captions: List<Caption>,
    selectedId: String?,
    positionMs: () -> Long,
    isPlaying: Boolean,
    onSelect: (Caption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val listState = rememberLazyListState()
    val activeIndex by remember(captions) {
        derivedStateOf { CaptionMotion.indexAt(captions, positionMs()) }
    }
    LaunchedEffect(activeIndex, isPlaying) {
        if (isPlaying && activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    val description = stringResource(R.string.captions_timeline)
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description }
            .testTag("caption_timeline"),
        contentPadding = PaddingValues(horizontal = GlassTheme.spacing.md),
        horizontalArrangement = Arrangement.Start,
    ) {
        itemsIndexed(captions, key = { _, caption -> caption.id }) { index, caption ->
            val gapBefore = caption.startMs - (captions.getOrNull(index - 1)?.endMs ?: 0L)
            val selected = caption.id == selectedId
            val active = index == activeIndex
            val blockDescription = stringResource(
                R.string.captions_block_description,
                caption.text.replace('\n', ' '),
                formatPrecise(caption.startMs),
                formatPrecise(caption.endMs),
            )
            Column(
                Modifier
                    .padding(start = gapWidth(gapBefore))
                    .width(blockWidth(caption.durationMs))
                    .fillMaxHeight()
                    .clip(GlassTheme.shapes.small)
                    .background(if (active) colors.accent.copy(alpha = 0.28f) else colors.glassFillStrong)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.accent else colors.glassBorderShade,
                        shape = GlassTheme.shapes.small,
                    )
                    .clickable(role = Role.Button) { onSelect(caption) }
                    .semantics {
                        this.selected = selected
                        contentDescription = blockDescription
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    caption.text.replace('\n', ' '),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatPrecise(caption.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
        item { Box(Modifier.width(GlassTheme.spacing.md)) }
    }
}

/** ~60 dp per second, never too thin to tap. */
private fun blockWidth(durationMs: Long): Dp = (durationMs / 1000f * 60f).coerceIn(56f, 320f).dp

private fun gapWidth(gapMs: Long): Dp = (gapMs / 1000f * 24f).coerceIn(4f, 48f).dp

/** m:ss.d, precise enough to retime captions. */
fun formatPrecise(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d.%d", t / 60_000, t % 60_000 / 1_000, t % 1_000 / 100)
}
