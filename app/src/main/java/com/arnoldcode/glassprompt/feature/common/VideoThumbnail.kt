package com.arnoldcode.glassprompt.feature.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.arnoldcode.glassprompt.core.designsystem.theme.GlassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Poster image from a thumbnail file, decoded off the main thread and kept in a small in-memory
 * cache so scrolling back and forth never re-reads the disk. Shows a film icon until it is ready
 * (or if there is none).
 */
@Composable
fun VideoThumbnail(path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState(initialValue = path?.let(ThumbnailMemoryCache::get), path) {
        if (path == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { ThumbnailMemoryCache.load(path) }
    }
    Box(modifier.background(GlassTheme.colors.glassFillStrong), contentAlignment = Alignment.Center) {
        Crossfade(bitmap, label = "thumbnail") { image ->
            if (image != null) {
                Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, tint = GlassTheme.colors.textTertiary)
                }
            }
        }
    }
}

/** Process-wide LRU of decoded posters, bounded by bytes (posters are ~360 px, ~0.5 MB each). */
private object ThumbnailMemoryCache {
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun load(path: String): Bitmap? = cache.get(path) ?: BitmapFactory.decodeFile(path)?.also { cache.put(path, it) }

    private const val MAX_BYTES = 12 * 1024 * 1024
}
