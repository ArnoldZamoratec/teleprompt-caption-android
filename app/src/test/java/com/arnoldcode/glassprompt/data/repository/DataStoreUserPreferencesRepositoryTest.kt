package com.arnoldcode.glassprompt.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arnoldcode.glassprompt.domain.model.ThemeMode
import com.arnoldcode.glassprompt.domain.model.UserPreferences
import com.arnoldcode.glassprompt.testing.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserPreferencesRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val dataStore = PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) {
        tmp.newFile("prefs.preferences_pb")
    }
    private val repository = DataStoreUserPreferencesRepository(dataStore, NoOpLogger)

    @Test
    fun `defaults when nothing stored`() = testScope.runTest {
        assertThat(repository.preferences.first()).isEqualTo(UserPreferences())
    }

    @Test
    fun `writes round-trip`() = testScope.runTest {
        repository.setOnboardingCompleted(true)
        repository.setThemeMode(ThemeMode.SYSTEM)
        repository.setReduceEffects(true)

        assertThat(repository.preferences.first()).isEqualTo(
            UserPreferences(onboardingCompleted = true, themeMode = ThemeMode.SYSTEM, reduceEffects = true),
        )
    }

    @Test
    fun `unknown stored theme falls back to default instead of crashing`() = testScope.runTest {
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }

        assertThat(repository.preferences.first().themeMode).isEqualTo(ThemeMode.DARK)
    }
}
