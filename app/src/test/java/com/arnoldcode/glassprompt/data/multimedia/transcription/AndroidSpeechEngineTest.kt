package com.arnoldcode.glassprompt.data.multimedia.transcription

import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidSpeechEngineTest {

    @Test
    fun `language matching prefers the exact tag, then the same language`() {
        val installed = listOf("en-US", "es-ES", "es-US")
        assertThat(AndroidSpeechEngine.bestMatch("es-US", installed)).isEqualTo("es-US")
        assertThat(AndroidSpeechEngine.bestMatch("es-PE", installed)).isEqualTo("es-ES")
        assertThat(AndroidSpeechEngine.bestMatch("EN-us", installed)).isEqualTo("en-US")
        assertThat(AndroidSpeechEngine.bestMatch("pt-BR", installed)).isNull()
    }

    @Test
    fun `capitalizes the first word and words after a long pause only`() {
        val words = listOf(
            CaptionWord("muchos", 0, 400),
            CaptionWord("fallan", 450, 800),
            CaptionWord("por", 1_300, 1_500), // 500 ms breath: same sentence
            CaptionWord("soy", 2_600, 2_900), // 1.1 s pause: new sentence
        )
        assertThat(AndroidSpeechEngine.capitalizeSentences(words).map { it.text })
            .containsExactly("Muchos", "fallan", "por", "Soy").inOrder()
    }
}
