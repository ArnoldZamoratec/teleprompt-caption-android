package com.arnoldcode.glassprompt.domain.transcription

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioProcessingTest {

    @Test
    fun `downsampling 48k to 16k keeps one sample in three, across chunk boundaries`() {
        val resampler = MonoResampler(48_000, 16_000)
        val total = (0 until 10).sumOf { resampler.process(FloatArray(4_801) { 0.5f }).size }
        assertThat(total).isEqualTo(48_010 / 3)
    }

    @Test
    fun `downsampling averages, so constant signals keep their level`() {
        val out = MonoResampler(44_100, 16_000).process(FloatArray(44_100) { 0.25f })
        assertThat(out.size).isIn(15_999..16_000)
        out.forEach { assertThat(abs(it - (0.25 * Short.MAX_VALUE).toInt())).isAtMost(1) }
    }

    @Test
    fun `downsampling attenuates noise above the new Nyquist frequency`() {
        // A 20 kHz tone is above the 8 kHz Nyquist limit of 16 kHz audio and must mostly vanish.
        val tone = FloatArray(48_000) { sin(2 * PI * 20_000 * it / 48_000).toFloat() }
        val out = MonoResampler(48_000, 16_000).process(tone)
        val peak = out.maxOf { abs(it.toInt()) } / Short.MAX_VALUE.toDouble()
        assertThat(peak).isLessThan(0.5)
    }

    @Test
    fun `upsampling interpolates linearly`() {
        val out = MonoResampler(8_000, 16_000).process(floatArrayOf(0f, 1f, 0f))
        assertThat(out.map { it.toInt() }).containsExactly(0, 16_383, 32_767, 16_383, 0).inOrder()
    }

    @Test
    fun `samples are clipped to 16 bits`() {
        val out = MonoResampler(16_000, 16_000).process(floatArrayOf(3f, -3f))
        assertThat(out.toList()).containsExactly(Short.MAX_VALUE, (-Short.MAX_VALUE).toShort()).inOrder()
    }

    @Test
    fun `level meter reports one level per full frame plus the remainder`() {
        val meter = LevelMeter(480)
        meter.add(ShortArray(700))
        meter.add(ShortArray(300) { 16_384 })
        val levels = meter.finish()
        assertThat(levels).hasLength(3)
        assertThat(levels[0]).isEqualTo(-96.0)
        assertThat(levels[2]).isWithin(0.1).of(-6.0)
    }

    @Test
    fun `long regions split at the quietest point`() {
        // 30 ms frames; 40 s of speech with a dip at 15 s.
        val db = DoubleArray(1_334) { if (it == 500) -60.0 else -20.0 }
        val parts = EnergyVad.splitLong(listOf(SpeechRegion(0, 40_000)), db, 20_000)
        assertThat(parts.first()).isEqualTo(SpeechRegion(0, 15_000))
        assertThat(parts.last().endMs).isEqualTo(40_000)
        parts.forEach { assertThat(it.durationMs).isAtMost(20_000) }
        parts.zipWithNext().forEach { (a, b) -> assertThat(b.startMs).isEqualTo(a.endMs) }
    }

    @Test
    fun `short regions are left alone`() {
        val regions = listOf(SpeechRegion(0, 5_000), SpeechRegion(6_000, 9_000))
        assertThat(EnergyVad.splitLong(regions, DoubleArray(300), 20_000)).isEqualTo(regions)
    }
}
