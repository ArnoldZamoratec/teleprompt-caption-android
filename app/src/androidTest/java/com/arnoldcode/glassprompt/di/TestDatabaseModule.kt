package com.arnoldcode.glassprompt.di

import android.content.Context
import androidx.room.Room
import com.arnoldcode.glassprompt.data.di.DatabaseModule
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.dao.TakeDao
import com.arnoldcode.glassprompt.data.local.database.GlassPromptDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Real Room stack, but in memory: every test starts empty and nothing touches the device's data. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlassPromptDatabase =
        Room.inMemoryDatabaseBuilder(context, GlassPromptDatabase::class.java).build()

    @Provides
    fun provideProjectDao(database: GlassPromptDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideTakeDao(database: GlassPromptDatabase): TakeDao = database.takeDao()
}
