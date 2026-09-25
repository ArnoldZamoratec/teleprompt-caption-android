package com.arnoldcode.glassprompt.feature.teleprompter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import kotlinx.coroutines.delay
import java.util.Locale

/** Hides the system bars while shown; a swipe from the edge reveals them temporarily. */
@Composable
fun ImmersiveMode() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Locks the screen orientation while shown (recording must not rotate mid-take). */
@Composable
fun LockOrientation(landscape: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(landscape) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Big "3, 2, 1" overlay. Counts down from [seconds] when [active] turns true, then calls
 * [onFinished]. Announced to TalkBack.
 */
@Composable
fun CountdownOverlay(active: Boolean, seconds: Int, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(active) {
        if (!active) {
            value = null
            return@LaunchedEffect
        }
        for (n in seconds downTo 1) {
            value = n
            delay(1_000)
        }
        value = null
        onFinished()
    }
    val current = value ?: return
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { (scaleIn(tween(250), initialScale = 1.6f) + fadeIn()) togetherWith (scaleOut(tween(200)) + fadeOut()) },
            label = "countdown",
        ) { n ->
            Text(
                n.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

/** "− 1.2x +" speed stepper and text size buttons shared by both prompter screens. */
@Composable
fun SpeedAndSizeControls(
    settings: TeleprompterSettings,
    onSpeedStep: (Boolean) -> Unit,
    onFontStep: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showSize: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xxs)) {
        GlassIconButton(Icons.Outlined.Remove, stringResource(R.string.prompter_slower), { onSpeedStep(false) }, size = 40.dp)
        Text(
            speedLabel(settings.speed),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.widthIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        GlassIconButton(Icons.Outlined.Add, stringResource(R.string.prompter_faster), { onSpeedStep(true) }, size = 40.dp)
        if (showSize) {
            GlassIconButton(Icons.Outlined.TextDecrease, stringResource(R.string.prompter_smaller), { onFontStep(false) }, size = 40.dp)
            GlassIconButton(Icons.Outlined.TextIncrease, stringResource(R.string.prompter_bigger), { onFontStep(true) }, size = 40.dp)
        }
    }
}

fun speedLabel(speed: Float): String = String.format(Locale.ROOT, "%.1fx", speed)
