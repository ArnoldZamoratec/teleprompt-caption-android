package com.arnoldcode.glassprompt.di

import com.arnoldcode.glassprompt.data.di.ExportSchedulerModule
import com.arnoldcode.glassprompt.domain.repository.ExportScheduler
import com.arnoldcode.glassprompt.testing.FakeExportScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** The Hilt test app has no WorkManager configuration: exports are driven by hand. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExportSchedulerModule::class])
abstract class TestExportModule {

    @Binds
    abstract fun bindScheduler(fake: FakeExportScheduler): ExportScheduler

    companion object {
        @Provides
        @Singleton
        fun provideFakeScheduler(): FakeExportScheduler = FakeExportScheduler()
    }
}
