package com.arnoldcode.glassprompt.domain.transcription

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnergyVadTest {

    private fun frames(vararg spans: Pair<Int, Double>): DoubleArray =
        spans.flatMap { (count, db) -> List(count) { db } }.toDoubleArray()

    @Test
    fun `silence has no speech`() {
        assertThat(EnergyVad.detect(frames(100 to -70.0), 3_000)).isEmpty()
        assertThat(EnergyVad.detect(DoubleArray(0), 0)).isEmpty()
    }

    @Test
    fun `loud stretch over quiet floor is one padded region`() {
        // 1 s quiet, 1.5 s speech, 1 s quiet (30 ms frames).
        val db = frames(33 to -60.0, 50 to -20.0, 33 to -60.0)
        val regions = EnergyVad.detect(db, 116L * 30)
        assertThat(regions).hasSize(1)
        assertThat(regions[0].startMs).isEqualTo(33L * 30 - 120)
        assertThat(regions[0].endMs).isEqualTo(83L * 30 + 120)
    }

    @Test
    fun `short breaths are bridged but long pauses split`() {
        val db = frames(
            20 to -60.0, 20 to -20.0,
            5 to -60.0, // 150 ms breath: bridged
            20 to -20.0,
            40 to -60.0, // 1.2 s pause: split
            20 to -20.0, 20 to -60.0,
        )
        assertThat(EnergyVad.detect(db, 145L * 30)).hasSize(2)
    }

    @Test
    fun `blips shorter than the minimum are dropped`() {
        val db = frames(40 to -60.0, 3 to -15.0, 40 to -60.0)
        assertThat(EnergyVad.detect(db, 83L * 30)).isEmpty()
    }

    @Test
    fun `threshold adapts to a noisy floor`() {
        // Street noise at -35 dB never counts; speech 12 dB above it does.
        val db = frames(40 to -35.0, 30 to -23.0, 40 to -35.0)
        val regions = EnergyVad.detect(db, 110L * 30)
        assertThat(regions).hasSize(1)
        assertThat(regions[0].startMs).isEqualTo(40L * 30 - 120)
    }

    @Test
    fun `regions never exceed the recording`() {
        val regions = EnergyVad.detect(frames(30 to -15.0), 900)
        assertThat(regions.single().startMs).isEqualTo(0)
        assertThat(regions.single().endMs).isEqualTo(900)
    }

    @Test
    fun `frame level in dBFS`() {
        assertThat(EnergyVad.frameDb(ShortArray(480))).isEqualTo(-96.0)
        val fullScale = ShortArray(480) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertThat(EnergyVad.frameDb(fullScale)).isWithin(0.01).of(0.0)
    }
}
