package com.arnoldcode.glassprompt.feature.teleprompter

import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.TextAlignment
import com.arnoldcode.glassprompt.domain.model.TextPosition
import com.arnoldcode.glassprompt.domain.teleprompter.ScrollEngine
import kotlinx.coroutines.isActive

/** Scroll position + play state of a prompter; hoisted so screens can restart or read progress. */
@Stable
class PrompterState(internal val scroll: ScrollState) {
    var playing by mutableStateOf(false)

    /** 0..1 reading progress. */
    val progress: Float get() = if (scroll.maxValue == 0) 0f else scroll.value / scroll.maxValue.toFloat()

    val atEnd: Boolean get() = scroll.maxValue > 0 && scroll.value >= scroll.maxValue

    suspend fun restart() {
        playing = false
        scroll.animateScrollTo(0)
    }
}

@Composable
fun rememberPrompterState(): PrompterState {
    val scroll = rememberScrollState()
    return remember(scroll) { PrompterState(scroll) }
}

/**
 * The scrolling script. Auto-scroll runs on the frame clock at a rate from [ScrollEngine];
 * dragging scrolls manually (auto-scroll pauses while the finger is down and resumes from the
 * new position), double tap toggles play, pinch changes the font size. The reading guide sits
 * at the height given by [TeleprompterSettings.position].
 */
@Composable
fun Prompter(
    text: String,
    wordCount: Int,
    settings: TeleprompterSettings,
    state: PrompterState,
    onTogglePlay: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    textColor: Color = Color.White,
) {
    val currentSettings by rememberUpdatedState(settings)
    BoxWithConstraints(modifier.testTag("prompter")) {
        val guideFraction = settings.position.guideFraction()
        val topPad = maxHeight * guideFraction
        val bottomPad = maxHeight * (1f - guideFraction)

        // Scroll rate: whole scroll distance over the reading time at the chosen speed.
        val maxScroll = state.scroll.maxValue.toFloat()
        val pixelsPerSecond = ScrollEngine.pixelsPerSecond(maxScroll, wordCount, settings.speed)
        val currentRate by rememberUpdatedState(pixelsPerSecond)

        LaunchedEffect(state.playing) {
            if (!state.playing) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val delta = ScrollEngine.delta(currentRate, now - last)
                last = now
                if (!state.scroll.isScrollInProgress) state.scroll.dispatchRawDelta(delta)
                if (state.atEnd) state.playing = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom { zoom -> onFontSizeChange(currentSettings.fontSizeSp * zoom) }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onTogglePlay() }, onTap = { onTap() }) }
                .verticalScroll(state.scroll)
                .graphicsLayer { if (settings.mirror) scaleX = -1f },
        ) {
            Spacer(Modifier.height(topPad))
            Text(
                text = text,
                style = TextStyle(
                    color = textColor,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
                    letterSpacing = settings.letterSpacing.em,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = if (settings.alignment == TextAlignment.CENTER) TextAlign.Center else TextAlign.Start,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = settings.horizontalMarginDp.dp),
            )
            Spacer(Modifier.height(bottomPad))
        }

        ReadingGuide(Modifier.align(Alignment.TopStart).offset(y = topPad - 1.dp))
        // Soft fades so lines enter and leave gently.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.12f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.12f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )
    }
}

@Composable
private fun ReadingGuide(modifier: Modifier = Modifier) {
    val accent = GlassTheme.colors.accent
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.15f), accent.copy(alpha = 0.15f), accent))),
    )
}

private fun TextPosition.guideFraction(): Float = when (this) {
    TextPosition.TOP -> 0.22f
    TextPosition.CENTER -> 0.45f
    TextPosition.BOTTOM -> 0.68f
}

/** Two-finger pinch that leaves one-finger drags to the scroll container. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Keeps the display awake while the composable is shown (reading and recording). */
@Composable
fun KeepScreenOn() {
    val view: View = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
