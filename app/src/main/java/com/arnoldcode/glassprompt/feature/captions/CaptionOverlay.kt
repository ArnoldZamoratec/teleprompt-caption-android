package com.arnoldcode.glassprompt.feature.captions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.arnoldcode.glassprompt.core.captions.CaptionRenderer
import com.arnoldcode.glassprompt.domain.captions.CaptionMotion
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionStyle

/**
 * Live caption preview drawn with the same [CaptionRenderer] as the export. [positionMs] is read
 * in the draw phase, so playback only redraws this canvas — nothing recomposes per frame.
 * While paused ([animated] false) the caption is shown fully in, so seeking to a caption's start
 * shows it instead of the first, transparent frame of its entrance.
 */
@Composable
fun CaptionOverlay(
    captions: List<Caption>,
    style: CaptionStyle,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
    animated: () -> Boolean = { true },
) {
    val renderer = remember { CaptionRenderer() }
    Canvas(modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val time = positionMs()
            val caption = CaptionMotion.captionAt(captions, time) ?: return@drawIntoCanvas
            val frame = CaptionMotion.frame(caption, style.animation, time)
            renderer.drawCaption(
                canvas.nativeCanvas, size.width, size.height, caption, style,
                if (animated()) frame else frame.copy(alpha = 1f, scale = 1f, offsetY = 0f),
            )
        }
    }
}
