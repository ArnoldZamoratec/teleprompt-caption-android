package com.arnoldcode.glassprompt.projects

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arnoldcode.glassprompt.MainActivity
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** End-to-end project flow on the real Room stack (in memory). */
@HiltAndroidTest
class ProjectFlowTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var preferences: FakeUserPreferencesRepository
    @Inject lateinit var projects: ProjectRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        preferences.reset(UserPreferences(onboardingCompleted = true))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun createProject_writeScript_isAutosavedAndListed() {
        composeRule.onNodeWithTag("home_new_project").performClick()
        composeRule.onNodeWithTag("project_setup_screen").assertIsDisplayed()

        // Blank name is rejected inline.
        composeRule.onNodeWithTag("setup_save").performClick()
        composeRule.onNodeWithText(str(R.string.setup_name_error)).assertIsDisplayed()

        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("setup_name"))).performTextInput("Mi primer video")
        composeRule.onNodeWithTag("setup_save").performClick()

        composeRule.onNodeWithTag("script_editor_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_field").performTextInput("Hola a todos, bienvenidos")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(str(R.string.editor_saved))).fetchSemanticsNodes().isNotEmpty() &&
                runBlocking { projects.observeProjects().first().firstOrNull()?.wordCount == 4 }
        }

        // Back from the editor returns Home (setup was replaced), where the project is listed.
        composeRule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Mi primer video").assertIsDisplayed()

        composeRule.onNodeWithText(str(R.string.nav_projects)).performClick()
        composeRule.onNodeWithText("Mi primer video").assertIsDisplayed()
    }

    @Test
    fun useTemplate_prefillsNameAndScript() {
        composeRule.onNodeWithText(str(R.string.nav_templates)).performClick()
        composeRule.onNodeWithTag("template_product_review").performClick()
        composeRule.onNodeWithText(str(R.string.templates_use)).performClick()

        composeRule.onNodeWithText(str(R.string.tpl_review_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("setup_save").performClick()

        composeRule.onNodeWithTag("script_editor_screen").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            runBlocking { (projects.observeProjects().first().firstOrNull()?.wordCount ?: 0) > 20 }
        }
        val project = runBlocking { projects.observeProjects().first().single() }
        assertThat(project.name).isEqualTo(str(R.string.tpl_review_title))
    }
}
