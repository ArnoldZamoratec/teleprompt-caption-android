package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.testing.FakeMediaStorage
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordingUseCasesTest {

    private val storage = FakeMediaStorage()
    private val takes = FakeTakeRepository(storage)

    @Test
    fun `prepare refuses to record when storage is nearly full`() {
        storage.freeBytes = MediaStorage.MIN_FREE_BYTES_TO_RECORD - 1
        assertThat(PrepareRecordingUseCase(storage)()).isEqualTo(AppResult.Failure(AppError.StorageFull))

        storage.freeBytes = MediaStorage.MIN_FREE_BYTES_TO_RECORD
        assertThat(PrepareRecordingUseCase(storage)()).isEqualTo(AppResult.Success("/takes/take_1.mp4"))
    }

    @Test
    fun `too-short recordings are discarded instead of saved`() = runTest {
        val result = SaveTakeUseCase(takes, storage)(NewTake("p", "/takes/x.mp4", 200, 1080, 1920, 30))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(takes.all()).isEmpty()
        assertThat(storage.deleted).containsExactly("/takes/x.mp4")
    }

    @Test
    fun `valid recordings are saved`() = runTest {
        val result = SaveTakeUseCase(takes, storage)(NewTake("p", "/takes/x.mp4", 5_000, 1080, 1920, 30))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(takes.all().single().durationMs).isEqualTo(5_000)
    }

    @Test
    fun `unchanged teleprompter settings are not written`() = runTest {
        val projects = FakeProjectRepository()
        val project = projects.seed("Demo", updatedAt = 1)
        projects.now = 99

        UpdateTeleprompterSettingsUseCase(projects)(project, project.teleprompter)
        assertThat(projects.project(project.id)!!.updatedAt).isEqualTo(1)

        UpdateTeleprompterSettingsUseCase(projects)(project, TeleprompterSettings(speed = 2f))
        assertThat(projects.project(project.id)!!.teleprompter.speed).isEqualTo(2f)
    }
}
