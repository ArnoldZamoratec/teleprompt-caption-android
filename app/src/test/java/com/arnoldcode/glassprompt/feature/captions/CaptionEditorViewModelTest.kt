package com.arnoldcode.glassprompt.feature.captions

import androidx.lifecycle.SavedStateHandle
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionAnimation
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionStyles
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.usecase.ObserveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveTakeUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.TranscriptionControlUseCase
import com.arnoldcode.glassprompt.testing.FakeCaptionRepository
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.arnoldcode.glassprompt.testing.FakeTranscriptionScheduler
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionEditorViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val tmp = TemporaryFolder()

    private val takes = FakeTakeRepository()
    private val captions = FakeCaptionRepository()
    private val scheduler = FakeTranscriptionScheduler()
    private val preferences = FakeUserPreferencesRepository()
    private var nextId = 0

    private val track = listOf(
        Caption("a", "uno dos", 0, 1_000, listOf(CaptionWord("uno", 0, 400), CaptionWord("dos", 400, 1_000))),
        Caption("b", "tres", 1_500, 2_500, listOf(CaptionWord("tres", 1_500, 2_500))),
    )

    private fun takeId(): String = runBlocking {
        val file = tmp.newFile("take.mp4").absolutePath
        takes.addTake(NewTake("p1", file, 5_000, 1080, 1920, 30)).getOrNull()!!
    }

    private fun seedTrack(takeId: String, style: CaptionStyle = CaptionStyle()) =
        captions.seed(CaptionTrack(takeId, track, style, "es-ES", "speech"))

    private fun viewModel(takeId: String) = CaptionEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CaptionEditorViewModel.ARG_TAKE_ID to takeId)),
        observeTake = ObserveTakeUseCase(takes),
        observeTrack = ObserveCaptionTrackUseCase(captions),
        saveTrack = SaveCaptionTrackUseCase(captions),
        transcription = TranscriptionControlUseCase(scheduler, preferences),
        ids = IdGenerator { "n${nextId++}" },
        appScope = CoroutineScope(mainDispatcherRule.dispatcher),
    )

    private fun TestScope.loaded(takeId: String): CaptionEditorViewModel = viewModel(takeId).also { advanceUntilIdle() }

    @Test
    fun `opening an uncaptioned take starts its transcription once`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        val vm = loaded(take)
        assertThat(scheduler.enqueued).containsExactly(Triple(take, TranscriptionMode.AUTO, false))
        assertThat(vm.uiState.value.isTranscribing).isTrue()
        assertThat(vm.uiState.value.hasTrack).isFalse()
    }

    @Test
    fun `a captioned take loads without transcribing`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)
        assertThat(scheduler.enqueued).isEmpty()
        assertThat(vm.uiState.value.captions).isEqualTo(track)
        assertThat(vm.uiState.value.engineId).isEqualTo("speech")
    }

    @Test
    fun `missing video file shows not found`() = runTest(mainDispatcherRule.dispatcher) {
        val take = runBlocking { takes.addTake(NewTake("p1", "/gone.mp4", 5_000, 1080, 1920, 30)).getOrNull()!! }
        val vm = loaded(take)
        assertThat(vm.uiState.value.take).isNull()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(scheduler.enqueued).isEmpty()
    }

    @Test
    fun `edits are autosaved after the debounce, not on every keystroke`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)

        vm.onTextChange("a", "hola")
        vm.onTextChange("a", "hola mundo")
        advanceTimeBy(CaptionEditorViewModel.AUTOSAVE_DEBOUNCE_MS / 2)
        assertThat(captions.saveCount).isEqualTo(0)
        advanceUntilIdle()

        assertThat(captions.saveCount).isEqualTo(1)
        assertThat(captions.track(take)!!.captions.first().text).isEqualTo("hola mundo")
        // Our own save echoing back must not reset the history.
        assertThat(vm.uiState.value.canUndo).isTrue()
    }

    @Test
    fun `typing in one caption is a single undo step, undo and redo restore it`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)

        vm.onTextChange("a", "h")
        vm.onTextChange("a", "ho")
        vm.onTextChange("a", "hola")
        vm.onUndo()
        assertThat(vm.uiState.value.captions.first().text).isEqualTo("uno dos")
        assertThat(vm.uiState.value.canRedo).isTrue()
        vm.onRedo()
        assertThat(vm.uiState.value.captions.first().text).isEqualTo("hola")
    }

    @Test
    fun `split at the playhead, merge back and delete`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)

        vm.onSplit("a", playheadMs = 450)
        assertThat(vm.uiState.value.captions.map { it.text }).containsExactly("uno", "dos", "tres").inOrder()
        vm.onMergeWithNext("a")
        assertThat(vm.uiState.value.captions.map { it.text }).containsExactly("uno dos", "tres").inOrder()

        vm.onSelect("b")
        vm.onDelete("b")
        assertThat(vm.uiState.value.captions.map { it.id }).containsExactly("a")
        assertThat(vm.uiState.value.selectedId).isNull()
    }

    @Test
    fun `nudge and insert respect the timeline`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)

        vm.onNudge("b", -100, 0)
        assertThat(vm.uiState.value.captions.last().startMs).isEqualTo(1_400)

        vm.onInsertAt(3_000, "nuevo")
        assertThat(vm.uiState.value.selected?.text).isEqualTo("nuevo")

        vm.onInsertAt(500, "encima")
        assertThat(vm.uiState.value.message).isNotNull()
    }

    @Test
    fun `presets replace the look but keep the grouping, manual changes become custom`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take, CaptionStyle(maxWordsPerLine = 4))
        val vm = loaded(take)

        vm.onPreset(CaptionPreset.NEON)
        assertThat(vm.uiState.value.style).isEqualTo(CaptionStyles.of(CaptionPreset.NEON).copy(maxWordsPerLine = 4))

        vm.onStyleChange { it.copy(animation = CaptionAnimation.KARAOKE) }
        assertThat(vm.uiState.value.style.preset).isEqualTo(CaptionPreset.CUSTOM)
        assertThat(vm.uiState.value.style.animation).isEqualTo(CaptionAnimation.KARAOKE)

        vm.onUndo()
        assertThat(vm.uiState.value.style.preset).isEqualTo(CaptionPreset.NEON)
    }

    @Test
    fun `changing words per line regroups every word`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)

        vm.onWordsPerLine(1)
        // One word per line, at most two lines per caption.
        assertThat(vm.uiState.value.captions.map { it.text }).containsExactly("uno\ndos", "tres").inOrder()
        assertThat(vm.uiState.value.style.maxWordsPerLine).isEqualTo(1)
    }

    @Test
    fun `a finished re-transcription replaces the captions and clears history`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)
        vm.onTextChange("a", "editado")
        advanceUntilIdle()

        vm.onRetranscribe(TranscriptionMode.SCRIPT)
        advanceUntilIdle()
        assertThat(scheduler.enqueued.last()).isEqualTo(Triple(take, TranscriptionMode.SCRIPT, true))

        captions.seed(CaptionTrack(take, listOf(Caption("z", "nuevo guion", 0, 2_000)), CaptionStyle(), "es-ES", "script-alignment"))
        scheduler.set(take, TranscriptionState.Done)
        advanceUntilIdle()

        assertThat(vm.uiState.value.captions.single().text).isEqualTo("nuevo guion")
        assertThat(vm.uiState.value.canUndo).isFalse()
        assertThat(vm.uiState.value.transcription).isEqualTo(TranscriptionState.Done)
    }

    @Test
    fun `flush saves pending edits right away`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        seedTrack(take)
        val vm = loaded(take)
        vm.onTextChange("b", "cuatro")
        vm.flush()
        advanceTimeBy(1)
        assertThat(captions.track(take)!!.captions.last().text).isEqualTo("cuatro")
    }
}
