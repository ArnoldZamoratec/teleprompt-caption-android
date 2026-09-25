package com.arnoldcode.glassprompt.feature.camera

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.camera.RecorderEvent
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.RecordingState
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.PrepareRecordingUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveTakeUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateTeleprompterSettingsUseCase
import com.arnoldcode.glassprompt.feature.teleprompter.PrompterSettingsSession
import com.arnoldcode.glassprompt.testing.FakeCameraController
import com.arnoldcode.glassprompt.testing.FakeMediaStorage
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.arnoldcode.glassprompt.testing.NoLifecycleOwner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val projects = FakeProjectRepository()
    private val storage = FakeMediaStorage()
    private val takes = FakeTakeRepository(storage)
    private val camera = FakeCameraController()

    private fun viewModel(recording: RecordingSettings = RecordingSettings()): CameraViewModel {
        val project = projects.seed("Demo", body = "uno dos tres cuatro")
        if (recording != RecordingSettings()) {
            runBlocking { projects.updateProject(project.copy(recording = recording)) }
        }
        return CameraViewModel(
            savedStateHandle = SavedStateHandle(mapOf(CameraViewModel.ARG_PROJECT_ID to project.id)),
            camera = camera,
            session = PrompterSettingsSession(GetProjectUseCase(projects), GetScriptUseCase(projects), UpdateTeleprompterSettingsUseCase(projects)),
            prepareRecording = PrepareRecordingUseCase(storage),
            saveTake = SaveTakeUseCase(takes, storage),
            appScope = CoroutineScope(mainDispatcherRule.dispatcher),
        )
    }

    private suspend fun CameraViewModel.readyAndBound() {
        bindCamera(NoLifecycleOwner)
    }

    @Test
    fun `binding reports the applied configuration after fallback`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel(RecordingSettings(CameraLens.BACK, resolution = VideoResolution.UHD_2160, frameRate = 60))
        advanceUntilIdle()
        vm.readyAndBound()

        val bound = vm.uiState.value.bound!!
        assertThat(bound.lens).isEqualTo(CameraLens.BACK)
        assertThat(bound.resolution).isEqualTo(VideoResolution.FHD_1080) // no 4K on this camera
        assertThat(bound.frameRate).isEqualTo(30) // no 60 fps either
        assertThat(vm.uiState.value.script).isEqualTo("uno dos tres cuatro")
    }

    @Test
    fun `bind failure shows the error and retry rebinds`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camera.bindError = AppError.CameraUnavailable()
        vm.readyAndBound()
        assertThat(vm.uiState.value.cameraError).isTrue()

        vm.onRetryCamera()
        assertThat(vm.uiState.value.bindAttempt).isEqualTo(1)
        assertThat(vm.uiState.value.cameraError).isFalse()
    }

    @Test
    fun `record runs the countdown, then records and plays the prompter`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()

        vm.prompterCommands.test {
            vm.onRecordClick()
            runCurrent()
            assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Countdown(3))
            advanceTimeBy(2_001)
            assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Countdown(1))
            advanceTimeBy(1_000)
            runCurrent()

            assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Recording(0))
            assertThat(camera.recordingFile).isEqualTo("/takes/take_1.mp4")
            assertThat(awaitItem()).isEqualTo(PrompterCommand.Play)
        }
    }

    @Test
    fun `tapping during the countdown cancels it`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()

        vm.onRecordClick()
        runCurrent()
        vm.onRecordClick()
        advanceUntilIdle()

        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Idle)
        assertThat(camera.recordingFile).isNull()
    }

    @Test
    fun `pause and resume drive camera and prompter, stop saves the take`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()
        vm.onRecordClick()
        advanceUntilIdle()
        camera.emit(RecorderEvent.Progress(1_500))
        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Recording(1_500))

        vm.onPauseResumeClick()
        assertThat(camera.paused).isTrue()
        camera.emit(RecorderEvent.Paused(1_600))
        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Paused(1_600))
        vm.onPauseResumeClick()
        assertThat(camera.paused).isFalse()
        camera.emit(RecorderEvent.Resumed(1_600))

        vm.onRecordClick() // stop
        assertThat(camera.stopped).isTrue()
        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Finalizing)
        camera.emit(RecorderEvent.Finished(durationMs = 4_000, width = 1080, height = 1920, error = null))
        advanceUntilIdle()

        val saved = takes.all().single()
        assertThat(vm.uiState.value.savedTakeId).isEqualTo(saved.id)
        assertThat(saved.durationMs).isEqualTo(4_000)
        assertThat(saved.height).isEqualTo(1920)
        assertThat(saved.frameRate).isEqualTo(30)
        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Idle)
    }

    @Test
    fun `storage full blocks recording with a message`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()
        storage.freeBytes = 10

        vm.onRecordClick()
        advanceUntilIdle()

        assertThat(vm.uiState.value.recording).isEqualTo(RecordingState.Idle)
        assertThat(vm.uiState.value.message).isEqualTo(R.string.error_storage_full)
        assertThat(camera.recordingFile).isNull()
    }

    @Test
    fun `failed recording discards its file`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()
        vm.onRecordClick()
        advanceUntilIdle()

        camera.emit(RecorderEvent.Finished(0, 0, 0, AppError.RecordingFailed()))
        advanceUntilIdle()

        assertThat(takes.all()).isEmpty()
        assertThat(storage.deleted).containsExactly("/takes/take_1.mp4")
        assertThat(vm.uiState.value.message).isEqualTo(R.string.error_recording)
    }

    @Test
    fun `back while recording asks, and confirming discards and exits`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()
        vm.onRecordClick()
        advanceUntilIdle()

        vm.onBackRequest()
        assertThat(vm.uiState.value.confirmDiscard).isTrue()
        vm.onDiscardConfirm()
        camera.emit(RecorderEvent.Finished(3_000, 1080, 1920, null))
        advanceUntilIdle()

        assertThat(vm.uiState.value.exit).isTrue()
        assertThat(takes.all()).isEmpty()
        assertThat(storage.deleted).contains("/takes/take_1.mp4")
    }

    @Test
    fun `back when idle exits immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBackRequest()
        assertThat(vm.uiState.value.exit).isTrue()
    }

    @Test
    fun `lens switch, torch and exposure respect capabilities`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.readyAndBound()

        vm.onSwitchLens()
        assertThat(vm.uiState.value.lens).isEqualTo(CameraLens.BACK)
        vm.onTorchToggle()
        assertThat(camera.torchOn).isTrue()
        vm.onExposureChange(50)
        assertThat(camera.exposure).isEqualTo(6)
        vm.onZoomTo(20f)
        assertThat(vm.zoomRatio.value).isEqualTo(8f)
    }

    @Test
    fun `speed changes are persisted to the project`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSpeedStep(up = true)
        vm.onSpeedStep(up = true)
        advanceUntilIdle()

        val projectId = projects.observeProjects().first().single().id
        assertThat(projects.project(projectId)!!.teleprompter.speed).isEqualTo(1.2f)
    }
}
