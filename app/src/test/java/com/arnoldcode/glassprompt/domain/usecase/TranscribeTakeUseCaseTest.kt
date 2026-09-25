package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.CaptionStyles
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.Transcript
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Reason
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Stage
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.transcription.AudioExtractor
import com.arnoldcode.glassprompt.domain.transcription.ExtractedAudio
import com.arnoldcode.glassprompt.domain.transcription.ScriptAlignmentEngine
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionEngine
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionRequest
import com.arnoldcode.glassprompt.testing.FakeCaptionRepository
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.arnoldcode.glassprompt.testing.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TranscribeTakeUseCaseTest {

    private val projects = FakeProjectRepository()
    private val takes = FakeTakeRepository()
    private val captions = FakeCaptionRepository()
    private val preferences = FakeUserPreferencesRepository(UserPreferences(transcriptionLanguage = "es-ES"))
    private val audio = FakeAudioExtractor()
    private val speech = FakeEngine("speech")

    private var nextId = 0
    private val useCase = TranscribeTakeUseCase(
        takes, projects, projects, captions, preferences, audio,
        listOf(speech, ScriptAlignmentEngine()),
        IdGenerator { "c${nextId++}" }, NoOpLogger,
    )

    private suspend fun takeWithScript(body: String): String {
        val project = projects.seed("Demo", body)
        return takes.addTake(NewTake(project.id, "/take.mp4", 10_000, 1080, 1920, 30)).getOrNull()!!
    }

    @Test
    fun `recognized speech becomes captions`() = runTest {
        val take = takeWithScript("guion")
        speech.words = listOf(CaptionWord("Hola", 1_000, 1_400), CaptionWord("a", 1_400, 1_500), CaptionWord("todos.", 1_500, 2_000))

        assertThat(useCase(take, TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Done)

        val track = captions.track(take)!!
        assertThat(track.engineId).isEqualTo("speech")
        assertThat(track.captions.single().text).isEqualTo("Hola a todos.")
        assertThat(speech.lastRequest!!.language).isEqualTo("es-ES")
        assertThat(speech.lastRequest!!.regions).isNotEmpty()
        assertThat(audio.discarded).isTrue()
    }

    @Test
    fun `falls back to the script when recognition fails`() = runTest {
        val take = takeWithScript("Uno dos tres")
        speech.fail = true

        assertThat(useCase(take, TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Done)

        val track = captions.track(take)!!
        assertThat(track.engineId).isEqualTo(ScriptAlignmentEngine.ID)
        assertThat(track.captions.joinToString(" ") { it.text }).isEqualTo("Uno dos tres")
        // Words sit inside the detected speech (2 s .. 6 s), padded by the VAD.
        assertThat(track.captions.first().startMs).isAtLeast(1_800)
    }

    @Test
    fun `script mode never calls the recognizer`() = runTest {
        val take = takeWithScript("Uno dos")
        useCase(take, TranscriptionMode.SCRIPT)
        assertThat(speech.lastRequest).isNull()
        assertThat(captions.track(take)!!.engineId).isEqualTo(ScriptAlignmentEngine.ID)
    }

    @Test
    fun `unavailable engines are skipped`() = runTest {
        val take = takeWithScript("Uno dos")
        speech.available = false
        useCase(take, TranscriptionMode.AUTO)
        assertThat(speech.lastRequest).isNull()
    }

    @Test
    fun `no audio track and no script fails with NO_AUDIO`() = runTest {
        val take = takeWithScript("")
        audio.hasAudio = false
        assertThat(useCase(take, TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Failed(Reason.NO_AUDIO))
    }

    @Test
    fun `no audio track still captions the script over the whole take`() = runTest {
        val take = takeWithScript("Uno dos")
        audio.hasAudio = false
        assertThat(useCase(take, TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Done)
        assertThat(captions.track(take)!!.captions.last().endMs).isAtMost(10_000)
        assertThat(speech.lastRequest).isNull()
    }

    @Test
    fun `silence and no script fails with NO_SPEECH`() = runTest {
        val take = takeWithScript("")
        audio.silent = true
        assertThat(useCase(take, TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Failed(Reason.NO_SPEECH))
    }

    @Test
    fun `missing take fails with NOT_FOUND`() = runTest {
        assertThat(useCase("nope", TranscriptionMode.AUTO)).isEqualTo(TranscriptionState.Failed(Reason.NOT_FOUND))
    }

    @Test
    fun `re-transcribing keeps the track style, new tracks use the default preset`() = runTest {
        val take = takeWithScript("Uno dos")
        preferences.setDefaultCaptionPreset(CaptionPreset.NEON)
        useCase(take, TranscriptionMode.SCRIPT)
        assertThat(captions.track(take)!!.style).isEqualTo(CaptionStyles.of(CaptionPreset.NEON))

        val custom = CaptionStyles.of(CaptionPreset.BOLD).copy(preset = CaptionPreset.CUSTOM, uppercase = false)
        captions.seed(captions.track(take)!!.copy(style = custom))
        useCase(take, TranscriptionMode.SCRIPT)
        assertThat(captions.track(take)!!.style).isEqualTo(custom)
    }

    @Test
    fun `reports stages in order`() = runTest {
        val take = takeWithScript("Uno dos")
        val stages = mutableListOf<Stage>()
        useCase(take, TranscriptionMode.AUTO) { stage, _ -> if (stages.lastOrNull() != stage) stages += stage }
        assertThat(stages).containsExactly(Stage.EXTRACTING_AUDIO, Stage.DETECTING_SPEECH, Stage.TRANSCRIBING, Stage.SAVING).inOrder()
    }

    /** 10 s take: silence, speech from 2 s to 6 s, silence. */
    private class FakeAudioExtractor : AudioExtractor {
        var hasAudio = true
        var silent = false
        var discarded = false

        override suspend fun extract(videoPath: String, onProgress: (Float) -> Unit): ExtractedAudio? {
            if (!hasAudio) return null
            onProgress(1f)
            val frames = DoubleArray(333) { i -> if (!silent && i in 67..200) -20.0 else -70.0 }
            return ExtractedAudio("/cache/a.pcm", 16_000, 10_000, frames)
        }

        override fun discard(audio: ExtractedAudio) {
            discarded = true
        }
    }

    private class FakeEngine(override val id: String) : TranscriptionEngine {
        var available = true
        var fail = false
        var words: List<CaptionWord> = listOf(CaptionWord("hola", 2_000, 2_500))
        var lastRequest: TranscriptionRequest? = null

        override suspend fun isAvailable(language: String) = available

        override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Float) -> Unit): Transcript {
            lastRequest = request
            if (fail) error("recognizer crashed")
            return Transcript(words, request.language, id, hasWordTimings = true)
        }
    }
}
