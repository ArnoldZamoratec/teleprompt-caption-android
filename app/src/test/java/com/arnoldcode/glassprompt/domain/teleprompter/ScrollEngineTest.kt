package com.arnoldcode.glassprompt.domain.teleprompter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrollEngineTest {

    @Test
    fun `speed 1 covers the script in the 150 wpm reading time`() {
        // 300 words at 150 wpm = 120 s; 6000 px / 120 s = 50 px/s.
        assertThat(ScrollEngine.pixelsPerSecond(6000f, 300, 1f)).isWithin(0.001f).of(50f)
        assertThat(ScrollEngine.pixelsPerSecond(6000f, 300, 2f)).isWithin(0.001f).of(100f)
    }

    @Test
    fun `rate is independent of font size - only distance changes`() {
        val small = ScrollEngine.pixelsPerSecond(3000f, 150, 1f)
        val big = ScrollEngine.pixelsPerSecond(6000f, 150, 1f)
        // Both finish in the same 60 s.
        assertThat(3000f / small).isWithin(0.01f).of(60f)
        assertThat(6000f / big).isWithin(0.01f).of(60f)
    }

    @Test
    fun `nothing to scroll or no words means no motion`() {
        assertThat(ScrollEngine.pixelsPerSecond(0f, 100, 1f)).isEqualTo(0f)
        assertThat(ScrollEngine.pixelsPerSecond(500f, 0, 1f)).isEqualTo(0f)
    }

    @Test
    fun `frame delta is proportional and long frames are capped`() {
        assertThat(ScrollEngine.delta(60f, 16_666_667)).isWithin(0.01f).of(1f)
        assertThat(ScrollEngine.delta(60f, 5_000_000_000)).isWithin(0.01f).of(6f) // capped at 100 ms
        assertThat(ScrollEngine.delta(60f, -1)).isEqualTo(0f)
    }

    @Test
    fun `speed steps by tenths within limits`() {
        assertThat(ScrollEngine.step(1f, up = true)).isEqualTo(1.1f)
        assertThat(ScrollEngine.step(1.25f, up = false)).isEqualTo(1.2f)
        assertThat(ScrollEngine.step(3f, up = true)).isEqualTo(3f)
        assertThat(ScrollEngine.step(0.1f, up = false)).isEqualTo(0.1f)
    }

    @Test
    fun `remaining time counts down to zero`() {
        assertThat(ScrollEngine.remainingSeconds(0f, 1000f, 50f)).isEqualTo(20)
        assertThat(ScrollEngine.remainingSeconds(1200f, 1000f, 50f)).isEqualTo(0)
        assertThat(ScrollEngine.remainingSeconds(0f, 1000f, 0f)).isEqualTo(0)
    }
}
