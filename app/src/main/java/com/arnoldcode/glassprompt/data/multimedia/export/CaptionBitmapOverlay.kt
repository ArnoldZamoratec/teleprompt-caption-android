package com.arnoldcode.glassprompt.data.multimedia.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.arnoldcode.glassprompt.core.captions.CaptionRenderer
import com.arnoldcode.glassprompt.domain.captions.CaptionMotion
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import androidx.core.graphics.createBitmap

/**
 * Burns captions into the exported video with the same [CaptionRenderer] as the editor preview,
 * so the export looks exactly like what the user saw.
 *
 * One full-frame bitmap is reused. It is only redrawn when what is on screen changes (another
 * caption, an animation step, the highlighted word); Media3 re-uploads the texture only when the
 * bitmap's generation id changes, so still frames cost nothing.
 */
@UnstableApi
class CaptionBitmapOverlay(
    captions: List<Caption>,
    private val style: CaptionStyle,
) : BitmapOverlay() {

    private val captions = captions.sortedBy { it.startMs }
    private val renderer = CaptionRenderer()
    private var bitmap: Bitmap? = null
    private var lastKey: Any? = UNSET

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        bitmap?.recycle()
        bitmap = createBitmap(videoSize.width, videoSize.height)
        lastKey = UNSET
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val target = checkNotNull(bitmap) { "configure() must be called first" }
        val timeMs = presentationTimeUs / 1000
        val caption = CaptionMotion.captionAt(captions, timeMs)
        val frame = caption?.let { CaptionMotion.frame(it, style.animation, timeMs) }
        val key = caption?.id to frame
        if (key != lastKey) {
            lastKey = key
            target.eraseColor(Color.TRANSPARENT)
            if (caption != null && frame != null) {
                renderer.drawCaption(Canvas(target), target.width.toFloat(), target.height.toFloat(), caption, style, frame)
            }
        }
        return target
    }

    override fun release() {
        super.release()
        bitmap?.recycle()
        bitmap = null
    }

    private companion object {
        val UNSET = Any()
    }
}
