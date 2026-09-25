package com.arnoldcode.glassprompt.core.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The [HazeState] glass surfaces sample from. `null` means "no backdrop available"
 * (previews, camera screens), in which case surfaces use a translucent tint only.
 */
val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Layer ordering for haze sources: surfaces only sample layers below their own. */
object GlassLayer {
    const val Background = 0f
    const val Content = 1f
}

/**
 * Root of every glass screen: draws the ambient [AuroraBackground], registers it as a blur
 * source and exposes the shared [HazeState] to descendants.
 *
 * Mark scrollable screen content with [glassContentSource] so floating bars blur it too.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalGlassHazeState provides hazeState) {
        Box(modifier.fillMaxSize()) {
            AuroraBackground(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = GlassLayer.Background),
            )
            content()
        }
    }
}

/**
 * Registers content as a blur source above the background, so overlays (top/bottom bars,
 * dialogs) refract it while glass cards inside the content still sample only the aurora.
 */
fun Modifier.glassContentSource(): Modifier = composed {
    val state = LocalGlassHazeState.current
    if (state == null) this else this.hazeSource(state, zIndex = GlassLayer.Content)
}
