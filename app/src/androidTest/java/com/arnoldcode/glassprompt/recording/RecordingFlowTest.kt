package com.arnoldcode.glassprompt.recording

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arnoldcode.glassprompt.MainActivity
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import java.io.File
import javax.inject.Inject

/** Records a real video with the device camera and checks it lands in Room and on disk. */
@HiltAndroidTest
class RecordingFlowTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    // Closes the activity outside the compose rule's test loop.
    @get:Rule(order = 1) val closeActivity = object : ExternalResource() {
        override fun after() {
            scenario?.close()
        }
    }
    @get:Rule(order = 2) val composeRule = createEmptyComposeRule()

    @Inject lateinit var preferences: FakeUserPreferencesRepository
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var takes: TakeRepository

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var projectId: String

    @Before
    fun setUp() {
        hiltRule.inject()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, it)
        }
        preferences.reset(UserPreferences(onboardingCompleted = true))
        projectId = runBlocking {
            val project = NewProject(
                name = "Prueba de grabación",
                teleprompter = TeleprompterSettings(countdownSeconds = 1),
                scriptBody = "Hola, esta es una prueba de grabación con teleprompter.",
            )
            (projects.createProject(project) as AppResult.Success).data
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun rehearsalTeleprompter_opensFromEditorAndPlays() {
        composeRule.onNodeWithText(str(R.string.nav_projects)).performClick()
        composeRule.onNodeWithText("Prueba de grabación").performClick()
        composeRule.onNodeWithText(str(R.string.editor_rehearse)).performClick()

        composeRule.onNodeWithTag("teleprompter_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("prompter_play").performClick()
        // Countdown of 1 s, then the play button turns into pause.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("prompter_play")).fetchSemanticsNodes().isNotEmpty() &&
                runCatching { composeRule.onNodeWithContentDescription(str(R.string.prompter_pause)).assertExists() }.isSuccess
        }
    }

    @Test
    fun recordTake_savesVideoAndOpensReview() {
        composeRule.onNodeWithText(str(R.string.nav_projects)).performClick()
        composeRule.onNodeWithText("Prueba de grabación").performClick()
        composeRule.onNodeWithTag("editor_record").performClick()

        composeRule.onNodeWithTag("camera_screen").assertIsDisplayed()
        // Wait for the camera to bind (record button becomes enabled).
        composeRule.waitUntil(15_000) {
            runCatching { composeRule.onNodeWithTag("camera_record").assertIsDisplayed().assertIsEnabled() }.isSuccess
        }
        composeRule.onNodeWithTag("camera_record").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasTestTag("camera_rec_indicator")).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(2_500)
        composeRule.onNodeWithTag("camera_record").performClick()

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasTestTag("video_review_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        val take = runBlocking { takes.observeTakes(projectId).first().single() }
        assertThat(take.durationMs).isAtLeast(1_500)
        assertThat(take.width).isGreaterThan(0)
        assertThat(take.height).isGreaterThan(take.width) // portrait project
        assertThat(File(take.filePath).length()).isGreaterThan(0)
        composeRule.onNodeWithTag("review_captions").assertIsDisplayed()
    }

}
