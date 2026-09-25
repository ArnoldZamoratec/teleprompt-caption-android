package com.arnoldcode.glassprompt.feature.captions

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassChip
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSlider
import com.arnoldcode.glassprompt.core.designsystem.component.GlassSwitchRow
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.CaptionAnimation
import com.arnoldcode.glassprompt.domain.model.CaptionPosition
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import kotlin.math.roundToInt

/** Presets plus every individual style control. Any manual change turns the style into "Personalizado". */
@Composable
fun CaptionStylePanel(
    style: CaptionStyle,
    onPreset: (CaptionPreset) -> Unit,
    onChange: ((CaptionStyle) -> CaptionStyle) -> Unit,
    onWordsPerLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GlassTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Label(R.string.captions_style_presets)
        ChipRow {
            CaptionPreset.entries.filter { it != CaptionPreset.CUSTOM }.forEach { preset ->
                GlassChip(stringResource(preset.labelRes()), selected = style.preset == preset, onClick = { onPreset(preset) })
            }
            if (style.preset == CaptionPreset.CUSTOM) {
                GlassChip(stringResource(CaptionPreset.CUSTOM.labelRes()), selected = true, onClick = {})
            }
        }

        // Sliders commit on release: dragging updates a local value so the history gets one step, not fifty.
        var size by remember(style.textSizeFraction) { mutableFloatStateOf(style.textSizeFraction) }
        GlassSlider(
            label = stringResource(R.string.captions_style_size),
            value = size,
            onValueChange = { size = it },
            onValueChangeFinished = { onChange { s -> s.copy(textSizeFraction = size) } },
            valueRange = CaptionStyle.SizeRange,
            valueText = "${(size / CaptionStyle().textSizeFraction * 100).roundToInt()} %",
        )
        var words by remember(style.maxWordsPerLine) { mutableFloatStateOf(style.maxWordsPerLine.toFloat()) }
        GlassSlider(
            label = stringResource(R.string.captions_style_words),
            value = words,
            onValueChange = { words = it },
            onValueChangeFinished = { onWordsPerLine(words.roundToInt()) },
            valueRange = CaptionStyle.WordsPerLineRange.first.toFloat()..CaptionStyle.WordsPerLineRange.last.toFloat(),
            steps = CaptionStyle.WordsPerLineRange.last - CaptionStyle.WordsPerLineRange.first - 1,
            valueText = words.roundToInt().toString(),
        )

        Label(R.string.captions_style_position)
        ChipRow {
            CaptionPosition.entries.forEach { position ->
                GlassChip(stringResource(position.labelRes()), selected = style.position == position, onClick = { onChange { it.copy(position = position) } })
            }
        }

        Label(R.string.captions_style_animation)
        ChipRow {
            CaptionAnimation.entries.forEach { animation ->
                GlassChip(stringResource(animation.labelRes()), selected = style.animation == animation, onClick = { onChange { it.copy(animation = animation) } })
            }
        }

        Label(R.string.captions_style_text_color)
        Swatches(TextColors, style.textColor) { color -> onChange { it.copy(textColor = color) } }
        Label(R.string.captions_style_highlight_color)
        Swatches(HighlightColors, style.highlightColor) { color -> onChange { it.copy(highlightColor = color) } }

        var background by remember(style.backgroundOpacity) { mutableFloatStateOf(style.backgroundOpacity) }
        GlassSlider(
            label = stringResource(R.string.captions_style_background),
            value = background,
            onValueChange = { background = it },
            onValueChangeFinished = { onChange { s -> s.copy(backgroundOpacity = background) } },
            valueText = "${(background * 100).roundToInt()} %",
        )
        var outline by remember(style.strokeWidth) { mutableFloatStateOf(style.strokeWidth) }
        GlassSlider(
            label = stringResource(R.string.captions_style_outline),
            value = outline,
            onValueChange = { outline = it },
            onValueChangeFinished = { onChange { s -> s.copy(strokeWidth = outline) } },
            valueRange = 0f..0.2f,
            valueText = "${(outline / 0.2f * 100).roundToInt()} %",
        )
        GlassSwitchRow(stringResource(R.string.captions_style_shadow), style.shadow, { v -> onChange { it.copy(shadow = v) } })
        GlassSwitchRow(stringResource(R.string.captions_style_uppercase), style.uppercase, { v -> onChange { it.copy(uppercase = v) } })
        GlassSwitchRow(stringResource(R.string.captions_style_bold), style.bold, { v -> onChange { it.copy(bold = v) } })
    }
}

@Composable
private fun Label(@StringRes res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = GlassTheme.colors.textSecondary)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
    ) { content() }
}

@Composable
private fun Swatches(colors: List<Pair<Int, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    val theme = GlassTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs),
    ) {
        colors.forEach { (argb, name) ->
            val isSelected = argb == selected
            val description = stringResource(R.string.captions_color, stringResource(name))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) theme.accent else theme.glassBorderShade, CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(argb) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = description
                    },
            )
        }
    }
}

private val TextColors = listOf(
    0xFFFFFFFF.toInt() to R.string.color_white,
    0xFFFFD60A.toInt() to R.string.color_yellow,
    0xFF7DF9FF.toInt() to R.string.color_cyan,
    0xFF3DD68C.toInt() to R.string.color_green,
    0xFFFF4DD8.toInt() to R.string.color_pink,
    0xFF000000.toInt() to R.string.color_black,
)

private val HighlightColors = listOf(
    0xFFFFD60A.toInt() to R.string.color_yellow,
    0xFF5B8CFF.toInt() to R.string.color_blue,
    0xFF3DD68C.toInt() to R.string.color_green,
    0xFFFF4DD8.toInt() to R.string.color_pink,
    0xFFFF6B3D.toInt() to R.string.color_orange,
    0xFFFFFFFF.toInt() to R.string.color_white,
)

@StringRes
fun CaptionPreset.labelRes(): Int = when (this) {
    CaptionPreset.CLASSIC -> R.string.caption_preset_classic
    CaptionPreset.BOLD -> R.string.caption_preset_bold
    CaptionPreset.MINIMAL -> R.string.caption_preset_minimal
    CaptionPreset.CREATOR -> R.string.caption_preset_creator
    CaptionPreset.NEON -> R.string.caption_preset_neon
    CaptionPreset.GLASS -> R.string.caption_preset_glass
    CaptionPreset.KARAOKE -> R.string.caption_preset_karaoke
    CaptionPreset.CUSTOM -> R.string.caption_preset_custom
}

@StringRes
private fun CaptionPosition.labelRes(): Int = when (this) {
    CaptionPosition.TOP -> R.string.caption_position_top
    CaptionPosition.CENTER -> R.string.caption_position_center
    CaptionPosition.BOTTOM -> R.string.caption_position_bottom
}

@StringRes
private fun CaptionAnimation.labelRes(): Int = when (this) {
    CaptionAnimation.NONE -> R.string.caption_animation_none
    CaptionAnimation.FADE -> R.string.caption_animation_fade
    CaptionAnimation.POP -> R.string.caption_animation_pop
    CaptionAnimation.SLIDE -> R.string.caption_animation_slide
    CaptionAnimation.SCALE -> R.string.caption_animation_scale
    CaptionAnimation.WORD_HIGHLIGHT -> R.string.caption_animation_word
    CaptionAnimation.KARAOKE -> R.string.caption_animation_karaoke
}
