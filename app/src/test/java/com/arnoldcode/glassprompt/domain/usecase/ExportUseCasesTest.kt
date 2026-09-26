package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.ExportSettings
import com.arnoldcode.glassprompt.domain.model.ExportState
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.testing.FakeCaptionRepository
import com.arnoldcode.glassprompt.testing.FakeExportRepository
import com.arnoldcode.glassprompt.testing.FakeMediaStorage
import com.arnoldcode.glassprompt.testing.FakeTakeRepository
import com.arnoldcode.glassprompt.testing.FakeVideoExporter
import com.arnoldcode.glassprompt.testing.FakeVideoGallery
import com.arnoldcode.glassprompt.testing.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class ExportUseCasesTest {

    private val storage = FakeMediaStorage()
    private val takes = FakeTakeRepository(storage)
    private val captions = FakeCaptionRepository()
    private val exports = FakeExportRepository()
    private val exporter = FakeVideoExporter()
    private val exportVideo = ExportVideoUseCase(takes, captions, exports, exporter, storage, NoOpLogger)

    private val settings = ExportSettings(VideoResolution.HD_720, 60, burnCaptions = true)

    private suspend fun take(fps: Int = 30): String =
        takes.addTake(NewTake("p1", "/takes/a.mp4", 20_000, 1080, 1920, fps)).getOrNull()!!

    private fun captionsFor(takeId: String) =
        captions.seed(CaptionTrack(takeId, listOf(Caption("c", "hola", 0, 1_000)), CaptionStyle(), "es-ES", "x"))

    @Test
    fun `renders, clamps the frame rate and records the export`() = runTest {
        val takeId = take(fps = 30)
        captionsFor(takeId)
        val progress = mutableListOf<Float>()

        val state = exportVideo(takeId, settings) { progress += it }

        val request = exporter.requests.single()
        assertThat(request.settings.frameRate).isEqualTo(30) // 60 asked, 30 recorded
        assertThat(request.captions).isNotNull()
        assertThat(request.outputPath).startsWith("/exports/")
        assertThat(progress).containsExactly(0.5f, 1f).inOrder()

        val export = exports.all().single()
        assertThat(state).isEqualTo(ExportState.Done(export.id))
        assertThat(export.withCaptions).isTrue()
        assertThat(export.frameRate).isEqualTo(30)
        assertThat(export.filePath).isEqualTo(request.outputPath)
    }

    @Test
    fun `captions are left out when not wanted or not available`() = runTest {
        val takeId = take()
        exportVideo(takeId, settings) {}
        assertThat(exporter.requests.last().captions).isNull()

        captionsFor(takeId)
        exportVideo(takeId, settings.copy(burnCaptions = false)) {}
        assertThat(exporter.requests.last().captions).isNull()
        assertThat(exports.all().none { it.withCaptions }).isTrue()
    }

    @Test
    fun `not enough space fails before rendering`() = runTest {
        val takeId = take()
        storage.freeBytes = 10_000_000
        assertThat(exportVideo(takeId, settings) {}).isEqualTo(ExportState.Failed(ExportState.Reason.STORAGE_FULL))
        assertThat(exporter.requests).isEmpty()
    }

    @Test
    fun `encoder failure deletes the partial file`() = runTest {
        val takeId = take()
        exporter.failWith = IllegalStateException("codec")
        assertThat(exportVideo(takeId, settings) {}).isEqualTo(ExportState.Failed(ExportState.Reason.ENCODER))
        assertThat(storage.deleted).containsExactly(exporter.requests.single().outputPath)
        assertThat(exports.all()).isEmpty()
    }

    @Test
    fun `cancellation deletes the partial file and propagates`() = runTest {
        val takeId = take()
        exporter.failWith = CancellationException("user cancelled")
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { exportVideo(takeId, settings) {} } }
        assertThat(storage.deleted).hasSize(1)
    }

    @Test
    fun `missing take fails with NOT_FOUND`() = runTest {
        assertThat(exportVideo("nope", settings) {}).isEqualTo(ExportState.Failed(ExportState.Reason.NOT_FOUND))
    }

    @Test
    fun `gallery copy is made once and remembered`() = runTest {
        val takeId = take()
        val id = (exportVideo(takeId, settings) {} as ExportState.Done).exportId
        val gallery = FakeVideoGallery()
        val save = SaveToGalleryUseCase(exports, gallery)

        val uri = save(exports.observeExport(id).first()!!).getOrNull()
        assertThat(uri).isEqualTo("content://media/video/1")
        assertThat(exports.observeExport(id).first()!!.galleryUri).isEqualTo(uri)
        assertThat(gallery.saved.single()).matches("GlassPrompt_Demo_\\d+\\.mp4")

        save(exports.observeExport(id).first()!!)
        assertThat(gallery.saved).hasSize(1)
    }
}
