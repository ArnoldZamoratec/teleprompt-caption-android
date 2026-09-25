package com.arnoldcode.glassprompt.data.repository

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
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
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

class DataStoreUserPreferencesRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    // Okio storage replaces the file atomically; the java.io one can't rename over an existing file on Windows.
    private val dataStore = PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
            File(tmp.root, "prefs.preferences_pb").toOkioPath()
        },
        scope = testScope.backgroundScope,
    )
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
