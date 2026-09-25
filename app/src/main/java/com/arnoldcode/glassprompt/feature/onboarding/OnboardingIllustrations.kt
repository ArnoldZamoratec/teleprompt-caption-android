package com.arnoldcode.glassprompt.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassCard
import com.arnoldcode.glassprompt.core.designsystem.glass.glassSurface
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import kotlin.math.sin

/** Decorative only: every illustration is hidden from accessibility services by the caller. */
@Composable
internal fun OnboardingIllustration(page: Int, modifier: Modifier = Modifier) {
    when (page) {
        0 -> PrompterIllustration(modifier)
        1 -> CaptionsIllustration(modifier)
        else -> ExportIllustration(modifier)
    }
}

/** A 0→1 loop, frozen at [staticValue] when effects are reduced. */
@Composable
private fun loopProgress(durationMs: Int, staticValue: Float = 0.35f): State<Float> {
    if (GlassTheme.reduceEffects) return remember { mutableFloatStateOf(staticValue) }
    val transition = rememberInfiniteTransition(label = "illustration")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
}

@Composable
private fun PrompterIllustration(modifier: Modifier) {
    val colors = GlassTheme.colors
    val lines = listOf(
        stringResource(R.string.onboarding_sample_line_1),
        stringResource(R.string.onboarding_sample_line_2),
        stringResource(R.string.onboarding_sample_line_3),
    )
    val progress by loopProgress(durationMs = 6_000)
    GlassCard(modifier = modifier.size(width = 240.dp, height = 300.dp), shape = GlassTheme.shapes.extraLarge) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(GlassTheme.shapes.medium)
                .fadingEdges(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = 120.dp.toPx() - progress * 240.dp.toPx() },
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(2) { lines.forEach { PrompterLine(it) } }
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.6f)),
            )
        }
        Spacer(Modifier.height(12.dp))
        RecPill()
    }
}

@Composable
private fun PrompterLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = GlassTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RecPill() {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .glassSurface(GlassTheme.shapes.pill, GlassTheme.elevation.flat)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.recording),
        )
        Text(stringResource(R.string.onboarding_sample_rec), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    }
}

@Composable
private fun CaptionsIllustration(modifier: Modifier) {
    val colors = GlassTheme.colors
    val progress by loopProgress(durationMs = 2_400)
    val words = stringResource(R.string.onboarding_sample_caption).split(" ")
    val highlighted = (progress * words.size).toInt().coerceIn(0, words.lastIndex)
    GlassCard(
        modifier = modifier.size(width = 280.dp, height = 240.dp),
        shape = GlassTheme.shapes.extraLarge,
        contentPadding = PaddingValues(20.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val bars = 28
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (bars - 1)) / bars
            repeat(bars) { i ->
                val wave = 0.25f + 0.75f * ((sin(i * 0.7f + progress * 6.283f * 2) + 1f) / 2f)
                val h = size.height * wave * 0.8f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(colors.accent, colors.accentSecondary)),
                    topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = buildAnnotatedString {
                words.forEachIndexed { i, word ->
                    val style = if (i == highlighted) {
                        SpanStyle(color = colors.onAccent, background = colors.accent, fontWeight = FontWeight.Bold)
                    } else {
                        SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    withStyle(style) { append(" $word ") }
                    if (i < words.lastIndex) append(" ")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(GlassTheme.shapes.medium, GlassTheme.elevation.flat)
                .padding(12.dp),
        )
    }
}

@Composable
private fun ExportIllustration(modifier: Modifier) {
    val colors = GlassTheme.colors
    val progress by loopProgress(durationMs = 3_200, staticValue = 0.78f)
    Column(
        modifier = modifier.fillMaxWidth(0.8f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = GlassTheme.shapes.large) {
            Text(stringResource(R.string.onboarding_sample_export), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(colors.glassFillStrong),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(colors.accent, colors.accentSecondary))),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Icons.Outlined.Share, Icons.Outlined.SmartDisplay, Icons.Outlined.PhotoCamera, Icons.Outlined.ChatBubbleOutline)
                .forEach { icon ->
                    Box(
                        Modifier
                            .size(56.dp)
                            .glassSurface(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
                    }
                }
        }
    }
}

/** Fades content out at the top and bottom edges (teleprompter look). */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.25f to Color.Black,
                0.75f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

