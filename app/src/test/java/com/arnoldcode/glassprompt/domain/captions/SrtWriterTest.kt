package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SrtWriterTest {

    @Test
    fun `timestamps use the SubRip format`() {
        assertThat(SrtWriter.timestamp(0)).isEqualTo("00:00:00,000")
        assertThat(SrtWriter.timestamp(3_723_045)).isEqualTo("01:02:03,045")
        assertThat(SrtWriter.timestamp(-5)).isEqualTo("00:00:00,000")
    }

    @Test
    fun `numbered cues in time order, blank captions skipped`() {
        val srt = SrtWriter.write(
            listOf(
                Caption("b", "Segundo\nlínea", 2_000, 3_500),
                Caption("x", "  ", 1_600, 1_900),
                Caption("a", "Primero", 0, 1_500),
            ),
        )
        assertThat(srt).isEqualTo(
            "1\n00:00:00,000 --> 00:00:01,500\nPrimero\n\n" +
                "2\n00:00:02,000 --> 00:00:03,500\nSegundo\nlínea\n\n",
        )
    }
}
