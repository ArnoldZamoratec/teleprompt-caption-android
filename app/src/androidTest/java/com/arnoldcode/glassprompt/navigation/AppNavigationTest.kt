package com.arnoldcode.glassprompt.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arnoldcode.glassprompt.MainActivity
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AppNavigationTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var preferences: FakeUserPreferencesRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = hiltRule.inject()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(prefs: UserPreferences) {
        preferences.reset(prefs)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun firstLaunch_showsOnboarding_andSkipLeadsHome() {
        launch(UserPreferences(onboardingCompleted = false))

        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.action_skip)).performClick()

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        assertThat(preferences.preferences.value.onboardingCompleted).isTrue()
    }

    @Test
    fun onboarding_nextThroughAllPages_thenStart() {
        launch(UserPreferences(onboardingCompleted = false))

        composeRule.onNodeWithText(str(R.string.onboarding_1_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_primary_button").performClick()
        composeRule.onNodeWithText(str(R.string.onboarding_2_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_primary_button").performClick()
        composeRule.onNodeWithText(str(R.string.onboarding_3_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.onboarding_start)).performClick()

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun returningUser_startsOnHome_withGreeting() {
        launch(UserPreferences(onboardingCompleted = true))

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.home_greeting_morning)).assertIsDisplayed()
    }

    @Test
    fun bottomBar_switchesBetweenTabs() {
        launch(UserPreferences(onboardingCompleted = true))

        composeRule.onNodeWithText(str(R.string.nav_settings)).performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithText(str(R.string.nav_projects)).performClick()
        composeRule.onNodeWithTag("projects_screen").assertIsDisplayed()

        composeRule.onNodeWithText(str(R.string.nav_home)).performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun newProjectHero_opensCreationFlow_andBackReturnsHome() {
        launch(UserPreferences(onboardingCompleted = true))

        composeRule.onNodeWithTag("home_new_project").performClick()
        composeRule.onNodeWithText(str(R.string.coming_soon_message)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
