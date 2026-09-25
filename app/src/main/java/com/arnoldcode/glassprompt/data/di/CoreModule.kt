package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.core.common.AndroidLogger
import com.arnoldcode.glassprompt.core.common.DefaultDispatcherProvider
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    }
}
