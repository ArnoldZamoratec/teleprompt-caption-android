package com.arnoldcode.glassprompt.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportOptionsTest {

    private fun take(width: Int, height: Int, fps: Int = 30) = Take("t", "p", "/t.mp4", 10_000, width, height, fps, 0)

    @Test
    fun `never offers more resolution than the take has`() {
        assertThat(ExportOptions.resolutions(take(1080, 1920))).containsExactly(VideoResolution.HD_720, VideoResolution.FHD_1080).inOrder()
        assertThat(ExportOptions.resolutions(take(2160, 3840))).containsExactly(*VideoResolution.entries.toTypedArray()).inOrder()
        // Smaller than 720p: still offers the smallest option (output will not upscale).
        assertThat(ExportOptions.resolutions(take(480, 640))).containsExactly(VideoResolution.HD_720)
    }

    @Test
    fun `never offers more frames than were recorded`() {
        assertThat(ExportOptions.frameRates(take(1080, 1920, fps = 30))).containsExactly(24, 30).inOrder()
        assertThat(ExportOptions.frameRates(take(1080, 1920, fps = 60))).containsExactly(24, 30, 60).inOrder()
        assertThat(ExportOptions.frameRates(take(1080, 1920, fps = 15))).containsExactly(15)
    }

    @Test
    fun `defaults to 1080p at the recorded rate`() {
        assertThat(ExportOptions.defaults(take(2160, 3840, fps = 60), hasCaptions = true))
            .isEqualTo(ExportSettings(VideoResolution.FHD_1080, 60, burnCaptions = true))
        assertThat(ExportOptions.defaults(take(720, 1280), hasCaptions = false))
            .isEqualTo(ExportSettings(VideoResolution.HD_720, 30, burnCaptions = false))
    }

    @Test
    fun `output keeps the aspect ratio with even sides`() {
        assertThat(ExportOptions.outputSize(1080, 1920, VideoResolution.HD_720)).isEqualTo(720 to 1280)
        assertThat(ExportOptions.outputSize(1920, 1080, VideoResolution.HD_720)).isEqualTo(1280 to 720)
        assertThat(ExportOptions.outputSize(1080, 2340, VideoResolution.HD_720)).isEqualTo(720 to 1560)
        // Never upscales.
        assertThat(ExportOptions.outputSize(720, 1280, VideoResolution.FHD_1080)).isEqualTo(720 to 1280)
        // Odd results are rounded down to even.
        assertThat(ExportOptions.outputSize(1080, 1921, VideoResolution.HD_720).second % 2).isEqualTo(0)
    }

    @Test
    fun `size estimate grows with pixels, frames and duration`() {
        val base = ExportOptions.estimateBytes(1080, 1920, 30, 60_000)
        // ~1080x1920x30x0.12 bit/s ≈ 7.5 Mbit/s + audio → roughly 57 MB per minute.
        assertThat(base).isIn(com.google.common.collect.Range.closed(50_000_000L, 65_000_000L))
        assertThat(ExportOptions.estimateBytes(720, 1280, 30, 60_000)).isLessThan(base)
        assertThat(ExportOptions.estimateBytes(1080, 1920, 60, 60_000)).isGreaterThan(base)
    }

    @Test
    fun `remaining time comes from the recent rate`() {
        val estimator = RemainingTimeEstimator(windowMs = 5_000)
        assertThat(estimator.update(0, 0f)).isNull()
        assertThat(estimator.update(500, 0.05f)).isNull() // not enough signal yet
        // 20 % in 2 s → 10 %/s → 8 s left.
        assertThat(estimator.update(2_000, 0.2f)).isEqualTo(8_000)
        // No progress: no estimate rather than infinity.
        val stalled = RemainingTimeEstimator()
        stalled.update(0, 0.5f)
        assertThat(stalled.update(3_000, 0.5f)).isNull()
    }
}
