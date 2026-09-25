package com.arnoldcode.glassprompt.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.data.local.database.GlassPromptDatabase
import com.arnoldcode.glassprompt.domain.model.CameraLens
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.TextPosition
import com.arnoldcode.glassprompt.domain.model.VideoResolution
import com.arnoldcode.glassprompt.testing.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoomProjectRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        GlassPromptDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var nowMillis = 1_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }
    private var counter = 0
    private val ids = IdGenerator { "id${++counter}" }

    private val projects = RoomProjectRepository(database.projectDao(), clock, ids, NoOpLogger)
    private val scripts = RoomScriptRepository(database.projectDao(), clock, NoOpLogger)

    @After
    fun tearDown() = database.close()

    private suspend fun create(name: String, body: String = "", project: NewProject = NewProject(name, scriptBody = body)): String =
        (projects.createProject(project) as AppResult.Success).data

    @Test
    fun createsProjectWithScriptAndRoundTripsAllSettings() = runTest {
        val recording = RecordingSettings(CameraLens.BACK, resolution = VideoResolution.UHD_2160, frameRate = 60)
        val teleprompter = TeleprompterSettings(speed = 1.7f, fontSizeSp = 48f, position = TextPosition.CENTER, mirror = true)
        val id = create("Demo", project = NewProject("Demo", recording, teleprompter, "Hola mundo"))

        val project = (projects.getProject(id) as AppResult.Success).data
        assertThat(project.recording).isEqualTo(recording)
        assertThat(project.teleprompter).isEqualTo(teleprompter)
        assertThat(project.createdAt).isEqualTo(1_000L)
        assertThat((scripts.getScript(project.scriptId) as AppResult.Success).data.body).isEqualTo("Hola mundo")
    }

    @Test
    fun summariesAreNewestFirstWithWordCounts() = runTest {
        create("Viejo", "uno dos tres")
        nowMillis = 2_000
        create("Nuevo", "uno")

        val summaries = projects.observeProjects().first()
        assertThat(summaries.map { it.name to it.wordCount }).containsExactly("Nuevo" to 1, "Viejo" to 3).inOrder()
    }

    @Test
    fun savingScriptUpdatesWordCountAndBumpsProjectToTop() = runTest {
        val first = create("Primero", "a")
        nowMillis = 2_000
        create("Segundo", "b")
        val scriptId = (projects.getProject(first) as AppResult.Success).data.scriptId

        projects.observeProjects().test {
            assertThat(awaitItem().first().name).isEqualTo("Segundo")
            nowMillis = 3_000
            scripts.saveBody(scriptId, "ahora tiene cuatro palabras")
            val updated = awaitItem()
            assertThat(updated.first().name).isEqualTo("Primero")
            assertThat(updated.first().wordCount).isEqualTo(4)
            assertThat(updated.first().updatedAt).isEqualTo(3_000)
        }
    }

    @Test
    fun duplicateCopiesScriptIndependently() = runTest {
        val id = create("Demo", "texto original")
        val copyId = (projects.duplicateProject(id, "Demo (2)") as AppResult.Success).data
        val copy = (projects.getProject(copyId) as AppResult.Success).data
        val original = (projects.getProject(id) as AppResult.Success).data

        scripts.saveBody(copy.scriptId, "texto cambiado")

        assertThat(copy.name).isEqualTo("Demo (2)")
        assertThat(copy.scriptId).isNotEqualTo(original.scriptId)
        assertThat((scripts.getScript(original.scriptId) as AppResult.Success).data.body).isEqualTo("texto original")
        assertThat(projects.projectNames()).containsExactly("Demo", "Demo (2)")
    }

    @Test
    fun deleteRemovesProjectAndItsScript() = runTest {
        val id = create("Borrar", "adiós")
        val scriptId = (projects.getProject(id) as AppResult.Success).data.scriptId

        assertThat(projects.deleteProject(id)).isEqualTo(AppResult.Success(Unit))

        assertThat(projects.observeProjects().first()).isEmpty()
        assertThat((scripts.getScript(scriptId) as AppResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun missingRowsMapToNotFound() = runTest {
        assertThat((projects.getProject("x") as AppResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
        assertThat((projects.deleteProject("x") as AppResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
        assertThat((scripts.saveBody("x", "b") as AppResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun updateChangesSettingsAndTimestamp() = runTest {
        val id = create("Antes")
        val project = (projects.getProject(id) as AppResult.Success).data
        nowMillis = 5_000

        projects.updateProject(project.copy(name = "Después", recording = RecordingSettings(frameRate = 24)))

        val updated = projects.observeProject(id).first()!!
        assertThat(updated.name).isEqualTo("Después")
        assertThat(updated.recording.frameRate).isEqualTo(24)
        assertThat(updated.updatedAt).isEqualTo(5_000)
    }
}
