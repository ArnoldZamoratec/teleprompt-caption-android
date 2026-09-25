package com.arnoldcode.glassprompt.core.designsystem.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.arnoldcode.glassprompt.core.designsystem.theme.LocalGlassAnimations

/** Springy shrink while pressed — the tactile micro-interaction shared by every glass control. */
fun Modifier.pressScale(interactionSource: InteractionSource, enabled: Boolean = true): Modifier = composed {
    val animations = LocalGlassAnimations.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) animations.pressedScale else 1f,
        animationSpec = animations.press(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
