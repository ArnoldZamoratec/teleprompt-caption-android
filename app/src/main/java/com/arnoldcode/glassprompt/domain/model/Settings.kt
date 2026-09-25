package com.arnoldcode.glassprompt.domain.model

enum class CameraLens { FRONT, BACK }

enum class VideoOrientation { PORTRAIT, LANDSCAPE }

enum class VideoResolution(val height: Int) { HD_720(720), FHD_1080(1080), UHD_2160(2160) }

/** Requested capture setup. The camera layer downgrades values the device cannot deliver. */
data class RecordingSettings(
    val lens: CameraLens = CameraLens.FRONT,
    val orientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val resolution: VideoResolution = VideoResolution.FHD_1080,
    val frameRate: Int = 30,
) {
    companion object {
        val FrameRates = listOf(24, 30, 60)
    }
}

enum class TextPosition { TOP, CENTER, BOTTOM }

enum class TextAlignment { START, CENTER }

/** How the script is displayed and scrolled. Speed 1.0 reads at roughly 150 words per minute. */
data class TeleprompterSettings(
    val speed: Float = 1f,
    val fontSizeSp: Float = 34f,
    val lineSpacing: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val horizontalMarginDp: Int = 24,
    val position: TextPosition = TextPosition.TOP,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val mirror: Boolean = false,
    val backgroundOpacity: Float = 0.55f,
    val countdownSeconds: Int = 3,
) {
    companion object {
        val SpeedRange = 0.1f..3f
        val FontSizeRange = 18f..72f
    }
}
