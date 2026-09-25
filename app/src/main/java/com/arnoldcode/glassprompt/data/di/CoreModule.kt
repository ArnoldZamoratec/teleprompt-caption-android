package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.core.common.AndroidLogger
import com.arnoldcode.glassprompt.core.common.ApplicationScope
import com.arnoldcode.glassprompt.core.common.DefaultDispatcherProvider
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger

    companion object {
        @Provides
        @Singleton
        fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        fun provideIdGenerator(): IdGenerator = IdGenerator.Uuid

        /** Outlives any screen: used for work that must finish after a ViewModel is cleared (final autosave). */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
