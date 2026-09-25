package com.arnoldcode.glassprompt.di

import com.arnoldcode.glassprompt.core.common.TimeProvider
import com.arnoldcode.glassprompt.data.di.DataModule
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import com.arnoldcode.glassprompt.testing.FakeUserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.LocalTime
import javax.inject.Singleton

/** Swaps disk-backed preferences for an in-memory fake so every test starts from a known state. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
abstract class TestDataModule {

    @Binds
    abstract fun bindPreferences(fake: FakeUserPreferencesRepository): UserPreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun provideFakePreferences(): FakeUserPreferencesRepository = FakeUserPreferencesRepository()

        /** Fixed morning clock so greeting assertions are deterministic. */
        @Provides
        fun provideTimeProvider(): TimeProvider = TimeProvider { LocalTime.of(9, 0) }
    }
}
