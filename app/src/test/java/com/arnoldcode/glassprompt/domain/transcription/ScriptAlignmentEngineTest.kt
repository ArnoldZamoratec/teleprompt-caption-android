package com.arnoldcode.glassprompt.domain.transcription

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScriptAlignmentEngineTest {

    @Test
    fun `words fill the speech proportionally to their length`() {
        val words = ScriptAlignmentEngine.align("hola mundo", listOf(SpeechRegion(0, 1_100)))
        assertThat(words.map { it.text }).containsExactly("hola", "mundo").inOrder()
        // Weights 5 and 6 over 1100 ms.
        assertThat(words[0].startMs).isEqualTo(0)
        assertThat(words[0].endMs).isEqualTo(500)
        assertThat(words[1].endMs).isEqualTo(1_100)
    }

    @Test
    fun `words are ordered, never overlap and stay inside speech`() {
        val regions = listOf(SpeechRegion(500, 2_000), SpeechRegion(3_000, 3_700), SpeechRegion(5_000, 7_000))
        val text = "Hoy vamos a hablar de algo importante: cómo grabar mejores videos sin perder tiempo."
        val words = ScriptAlignmentEngine.align(text, regions)

        assertThat(words).hasSize(14)
        words.zipWithNext().forEach { (a, b) -> assertThat(b.startMs).isAtLeast(a.endMs) }
        words.forEach { word ->
            assertThat(word.endMs).isAtLeast(word.startMs)
            assertThat(regions.any { word.startMs >= it.startMs && word.endMs <= it.endMs }).isTrue()
        }
    }

    @Test
    fun `punctuation-only tokens are skipped`() {
        val words = ScriptAlignmentEngine.align("Uno - dos ... tres", listOf(SpeechRegion(0, 3_000)))
        assertThat(words.map { it.text }).containsExactly("Uno", "dos", "tres").inOrder()
    }

    @Test
    fun `nothing to align gives no words`() {
        assertThat(ScriptAlignmentEngine.align("   ", listOf(SpeechRegion(0, 1_000)))).isEmpty()
        assertThat(ScriptAlignmentEngine.align("hola", emptyList())).isEmpty()
    }

    @Test
    fun `without detected speech the whole take is used`() = runTest {
        val request = TranscriptionRequest("unused.pcm", 16_000, 2_000, emptyList(), "a b", "es-ES")
        val transcript = ScriptAlignmentEngine().transcribe(request) {}
        assertThat(transcript.words.last().endMs).isEqualTo(2_000)
        assertThat(transcript.hasWordTimings).isFalse()
        assertThat(transcript.engineId).isEqualTo(ScriptAlignmentEngine.ID)
    }

    @Test
    fun `long scripts over many pauses align in linear time`() {
        // ~2.5 h of speech: the old per-word rescan of every region was quadratic here.
        val regions = List(3_000) { SpeechRegion(it * 3_000L, it * 3_000L + 2_500) }
        val text = List(20_000) { "palabra$it" }.joinToString(" ")
        val started = System.nanoTime()
        val words = ScriptAlignmentEngine.align(text, regions)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertThat(words).hasSize(20_000)
        words.zipWithNext().forEach { (a, b) -> assertThat(b.startMs).isAtLeast(a.endMs) }
        assertThat(elapsedMs).isLessThan(1_000)
    }
}
