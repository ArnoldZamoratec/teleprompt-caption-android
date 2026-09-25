package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptionSegmenterTest {

    /** Evenly spaced words, [each] ms long, no gaps. */
    private fun words(text: String, start: Long = 0, each: Long = 300): List<CaptionWord> =
        text.split(" ").mapIndexed { i, w -> CaptionWord(w, start + i * each, start + (i + 1) * each) }

    private fun segment(words: List<CaptionWord>, perLine: Int = 3) = CaptionSegmenter.segment(words, perLine) { "c$it" }

    @Test
    fun `caption holds at most two lines of words`() {
        val captions = segment(words("a b c d e f g h"))
        assertThat(captions.map { it.text }).containsExactly("a b c\nd e f", "g h").inOrder()
    }

    @Test
    fun `sentence end forces a break`() {
        val captions = segment(words("Hola. Qué tal"))
        assertThat(captions.map { it.text }).containsExactly("Hola.", "Qué tal").inOrder()
    }

    @Test
    fun `long pause forces a break`() {
        val captions = segment(words("uno dos") + words("tres", start = 2_000))
        assertThat(captions.map { it.text }).containsExactly("uno dos", "tres").inOrder()
    }

    @Test
    fun `comma breaks only after a full line`() {
        assertThat(segment(words("sí, claro que sí"))).hasSize(1)
        assertThat(segment(words("uno dos tres, cuatro")).map { it.text }).containsExactly("uno dos tres,", "cuatro").inOrder()
    }

    @Test
    fun `captions do not stay on screen too long`() {
        segment(words("a b c d e f", each = 1_000), perLine = 5).forEach {
            assertThat(it.durationMs).isAtMost(CaptionSegmenter.MAX_CAPTION_MS)
        }
    }

    @Test
    fun `short captions linger without overlapping the next one`() {
        val captions = segment(listOf(CaptionWord("Sí.", 0, 100), CaptionWord("Vale", 400, 700)))
        assertThat(captions[0].endMs).isEqualTo(400)
        assertThat(captions[1].endMs).isEqualTo(400 + CaptionSegmenter.MIN_CAPTION_MS)
    }

    @Test
    fun `keeps word timings and assigns ids`() {
        val captions = segment(words("a b c d e f g"))
        assertThat(captions.map { it.id }).containsExactly("c0", "c1").inOrder()
        assertThat(captions.flatMap { it.words }.map { it.text }).containsExactly("a", "b", "c", "d", "e", "f", "g").inOrder()
    }

    @Test
    fun `balanced lines`() {
        assertThat(CaptionSegmenter.lines(listOf("a", "b", "c", "d", "e"), 3)).isEqualTo("a b c\nd e")
        assertThat(CaptionSegmenter.lines(listOf("a", "b"), 3)).isEqualTo("a b")
    }
}
