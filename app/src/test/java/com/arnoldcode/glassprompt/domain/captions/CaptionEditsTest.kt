package com.arnoldcode.glassprompt.domain.captions

import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptionEditsTest {

    private val track = listOf(
        Caption("a", "uno dos", 0, 1_000, listOf(CaptionWord("uno", 0, 400), CaptionWord("dos", 400, 1_000))),
        Caption("b", "tres cuatro cinco", 1_500, 3_000),
        Caption("c", "seis", 4_000, 5_000),
    )

    private fun List<Caption>.assertValid(duration: Long) {
        zipWithNext().forEach { (x, y) -> assertThat(y.startMs).isAtLeast(x.endMs) }
        forEach {
            assertThat(it.durationMs).isAtLeast(CaptionEdits.MIN_DURATION_MS)
            assertThat(it.startMs).isAtLeast(0)
            assertThat(it.endMs).isAtMost(duration)
        }
    }

    @Test
    fun `edit text re-spreads word timings over the caption`() {
        val a = CaptionEdits.editText(track, "a", "  hola  mundo ").first()
        assertThat(a.text).isEqualTo("hola  mundo")
        assertThat(a.words.map { it.text }).containsExactly("hola", "mundo").inOrder()
        assertThat(a.words.first().startMs).isEqualTo(0)
        assertThat(a.words.last().endMs).isEqualTo(1_000)
    }

    @Test
    fun `retime is clamped between neighbours and keeps a minimum length`() {
        val b = CaptionEdits.retime(track, "b", startMs = 500, endMs = 9_000, videoDurationMs = 6_000).first { it.id == "b" }
        assertThat(b.startMs).isEqualTo(1_000)
        assertThat(b.endMs).isEqualTo(4_000)

        val squashed = CaptionEdits.retime(track, "c", startMs = 5_900, endMs = 5_950, videoDurationMs = 6_000)
        squashed.assertValid(6_000)
        assertThat(squashed.last().startMs).isEqualTo(5_800)
    }

    @Test
    fun `retime rescales word timings`() {
        val a = CaptionEdits.retime(track, "a", 0, 500, 6_000).first()
        assertThat(a.words.map { it.startMs to it.endMs }).containsExactly(0L to 200L, 200L to 500L).inOrder()
    }

    @Test
    fun `split uses word timings when present`() {
        val result = CaptionEdits.split(track, "a", wordIndex = 1, newId = "a2")
        assertThat(result.map { it.text }).containsExactly("uno", "dos", "tres cuatro cinco", "seis").inOrder()
        assertThat(result[0].endMs).isEqualTo(400)
        assertThat(result[1].startMs).isEqualTo(400)
        assertThat(result[1].id).isEqualTo("a2")
        result.assertValid(6_000)
    }

    @Test
    fun `split without word timings splits by word share`() {
        val result = CaptionEdits.split(track, "b", newId = "b2")
        assertThat(result.map { it.text }).containsExactly("uno dos", "tres", "cuatro cinco", "seis").inOrder()
        assertThat(result[1].endMs).isEqualTo(2_000)
        result.assertValid(6_000)
    }

    @Test
    fun `single words are not split`() {
        assertThat(CaptionEdits.split(track, "c", newId = "x")).isEqualTo(track)
    }

    @Test
    fun `merge joins text, time and words with the next caption`() {
        val merged = CaptionEdits.mergeWithNext(track, "a")
        assertThat(merged).hasSize(2)
        assertThat(merged[0].text).isEqualTo("uno dos tres cuatro cinco")
        assertThat(merged[0].endMs).isEqualTo(3_000)
        assertThat(CaptionEdits.mergeWithNext(track, "c")).isEqualTo(track)
    }

    @Test
    fun `insert fits into the gap it falls in`() {
        val inserted = CaptionEdits.insert(track, atMs = 3_200, text = "nuevo", newId = "n", videoDurationMs = 6_000)
        val n = inserted.first { it.id == "n" }
        assertThat(n.startMs).isEqualTo(3_200)
        assertThat(n.endMs).isEqualTo(4_000)
        inserted.assertValid(6_000)
    }

    @Test
    fun `insert over an existing caption or without room does nothing`() {
        assertThat(CaptionEdits.insert(track, 500, "x", "n", 6_000)).isEqualTo(track)
        assertThat(CaptionEdits.insert(track, 3_900, "x", "n", 6_000)).isEqualTo(track)
    }

    @Test
    fun `delete removes by id`() {
        assertThat(CaptionEdits.delete(track, "b").map { it.id }).containsExactly("a", "c").inOrder()
    }
}
