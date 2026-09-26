package com.arnoldcode.glassprompt.feature.export

import androidx.lifecycle.SavedStateHandle
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.usecase.ExportControlUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveTakeUseCase
import com.arnoldcode.glassprompt.testing.FakeCaptionRepository
import com.arnoldcode.glassprompt.testing.FakeExportScheduler
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val tmp = TemporaryFolder()

    private val takes = FakeTakeRepository()
    private val captions = FakeCaptionRepository()
    private val scheduler = FakeExportScheduler()

    private fun takeId(fps: Int = 30, withCaptions: Boolean = true): String = runBlocking {
        val id = takes.addTake(NewTake("p1", tmp.newFile().absolutePath, 10_000, 1080, 1920, fps)).getOrNull()!!
        if (withCaptions) captions.seed(CaptionTrack(id, listOf(Caption("c", "hola", 0, 900)), CaptionStyle(), "es-ES", "x"))
        id
    }

    private fun viewModel(takeId: String) = ExportViewModel(
        SavedStateHandle(mapOf(ExportViewModel.ARG_TAKE_ID to takeId)),
        ObserveTakeUseCase(takes),
        ObserveCaptionTrackUseCase(captions),
        ExportControlUseCase(scheduler),
    )

    @Test
    fun `offers the take's honest options with sensible defaults`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(takeId(fps = 30))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertThat(state.resolutions).containsExactly(VideoResolution.HD_720, VideoResolution.FHD_1080).inOrder()
        assertThat(state.frameRates).containsExactly(24, 30).inOrder()
        assertThat(state.settings).isEqualTo(ExportSettings(VideoResolution.FHD_1080, 30, burnCaptions = true))
        assertThat(state.outputSize).isEqualTo(1080 to 1920)
        assertThat(state.estimatedBytes).isGreaterThan(0)
    }

    @Test
    fun `changing options updates size and estimate`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(takeId())
        advanceUntilIdle()
        val before = vm.uiState.value.estimatedBytes
        vm.onResolution(VideoResolution.HD_720)
        vm.onFrameRate(24)
        assertThat(vm.uiState.value.outputSize).isEqualTo(720 to 1280)
        assertThat(vm.uiState.value.estimatedBytes).isLessThan(before)
    }

    @Test
    fun `captions cannot be turned on for an uncaptioned take`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(takeId(withCaptions = false))
        advanceUntilIdle()
        assertThat(vm.uiState.value.settings?.burnCaptions).isFalse()
        vm.onCaptions(true)
        assertThat(vm.uiState.value.settings?.burnCaptions).isFalse()
    }

    @Test
    fun `start exports once, locks options, and finishing reports the export`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        val vm = viewModel(take)
        advanceUntilIdle()

        vm.onStart()
        advanceUntilIdle()
        vm.onStart()
        vm.onResolution(VideoResolution.HD_720)
        assertThat(scheduler.started).hasSize(1)
        assertThat(vm.uiState.value.isExporting).isTrue()
        assertThat(vm.uiState.value.settings?.resolution).isEqualTo(VideoResolution.FHD_1080)

        scheduler.set(take, ExportState.Done("e1"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.finishedExportId).isEqualTo("e1")

        vm.onFinishedShown()
        assertThat(vm.uiState.value.finishedExportId).isNull()
        assertThat(scheduler.cleared).containsExactly(take)
    }

    @Test
    fun `a result left over from an earlier visit is ignored`() = runTest(mainDispatcherRule.dispatcher) {
        val take = takeId()
        scheduler.set(take, ExportState.Done("old"))
        val vm = viewModel(take)
        advanceUntilIdle()
        assertThat(vm.uiState.value.finishedExportId).isNull()
        assertThat(vm.uiState.value.export).isEqualTo(ExportState.Idle)
    }

    @Test
    fun `missing video shows not found`() = runTest(mainDispatcherRule.dispatcher) {
        val id = runBlocking { takes.addTake(NewTake("p1", "/gone.mp4", 1_000, 1080, 1920, 30)).getOrNull()!! }
        val vm = viewModel(id)
        advanceUntilIdle()
        assertThat(vm.uiState.value.take).isNull()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }
}
