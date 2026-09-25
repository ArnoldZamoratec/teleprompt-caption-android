package com.arnoldcode.glassprompt.core.captions

import com.arnoldcode.glassprompt.domain.captions.CaptionFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptionRendererTest {

    private fun frame(highlight: Int = -1, filled: Int = -1) = CaptionFrame(1f, 1f, 0f, highlight, filled)

    @Test
    fun `highlights the spoken word, across line breaks`() {
        val ranges = CaptionRenderer.highlightedRanges("uno dos\ntres", 3, frame(highlight = 2))
        assertThat(ranges).containsExactly(8..11)
    }

    @Test
    fun `karaoke fills every word spoken so far`() {
        val ranges = CaptionRenderer.highlightedRanges("uno dos tres", 3, frame(filled = 1))
        assertThat(ranges).containsExactly(0..2, 4..6).inOrder()
    }

    @Test
    fun `mismatched word counts map proportionally`() {
        // 4 timed words, but the text was edited down to 2.
        assertThat(CaptionRenderer.highlightedRanges("hola mundo", 4, frame(highlight = 3))).containsExactly(5..9)
    }

    @Test
    fun `nothing to highlight`() {
        assertThat(CaptionRenderer.highlightedRanges("hola", 1, frame())).isEmpty()
        assertThat(CaptionRenderer.highlightedRanges("hola", 0, frame(highlight = 0))).isEmpty()
    }
}
