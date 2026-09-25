package com.arnoldcode.glassprompt.di

import com.arnoldcode.glassprompt.data.di.TranscriptionSchedulerModule
import com.arnoldcode.glassprompt.domain.repository.TranscriptionScheduler
import com.arnoldcode.glassprompt.testing.FakeTranscriptionScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** The Hilt test app has no WorkManager configuration: transcriptions are driven by hand. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TranscriptionSchedulerModule::class])
abstract class TestTranscriptionModule {

    @Binds
    abstract fun bindScheduler(fake: FakeTranscriptionScheduler): TranscriptionScheduler

    companion object {
        @Provides
        @Singleton
        fun provideFakeScheduler(): FakeTranscriptionScheduler = FakeTranscriptionScheduler()
    }
}
