package com.arnoldcode.glassprompt.feature.common

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.common.util.UnstableApi
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.designsystem.component.GlassIconButton
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** Player + observable position, so overlays (captions) can follow playback. */
@Stable
class VideoPlayerState internal constructor(val player: ExoPlayer) {
    var positionMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableLongStateOf(0L)
        internal set
    var isPlaying by mutableStateOf(false)
        internal set

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    fun pause() = player.pause()
}

/** ExoPlayer for a local file, released when it leaves composition. Position updates at ~30 Hz while playing. */
@Composable
fun rememberVideoPlayerState(filePath: String): VideoPlayerState {
    val context = LocalContext.current
    val state = remember(filePath) {
        VideoPlayerState(
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
                prepare()
            },
        )
    }
    DisposableEffect(state) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) state.durationMs = state.player.duration.coerceAtLeast(0)
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
            state.player.release()
        }
    }
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying) {
            state.positionMs = state.player.currentPosition
            delay(33)
        }
        state.positionMs = state.player.currentPosition
    }
    return state
}

/**
 * Video surface in a rounded frame with play/pause and a seek bar. [overlay] draws on top of
 * the video (used for live caption preview).
 */
@Composable
@OptIn(UnstableApi::class)
fun VideoPlayerView(
    state: VideoPlayerState,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    /** Width of the video relative to the player; the playback bar always spans the full width. */
    videoWidthFraction: Float = 1f,
    overlay: @Composable () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
        Box(
            Modifier
                .fillMaxWidth(videoWidthFraction)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(aspectRatio.coerceIn(0.3f, 3f))
                .clip(GlassTheme.shapes.large)
                .background(Color.Black),
        ) {
            ContentFrame(player = state.player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            overlay()
        }
        PlaybackBar(state)
    }
}

@Composable
fun PlaybackBar(state: VideoPlayerState, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val positionText = formatClock(state.positionMs) + " / " + formatClock(state.durationMs)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GlassTheme.spacing.xs)) {
        GlassIconButton(
            icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
            onClick = state::togglePlay,
            size = 44.dp,
        )
        Slider(
            value = if (state.durationMs > 0) state.positionMs / state.durationMs.toFloat() else 0f,
            onValueChange = { state.seekTo((it * state.durationMs).toLong()) },
            colors = SliderDefaults.colors(thumbColor = colors.textPrimary, activeTrackColor = colors.accent, inactiveTrackColor = colors.glassFillStrong),
            modifier = Modifier.weight(1f).semantics { contentDescription = positionText },
        )
        Text(positionText, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
    }
}

/** m:ss (or h:mm:ss) for players and lists. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
