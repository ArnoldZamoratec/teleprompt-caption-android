package com.arnoldcode.glassprompt.data.di

import android.content.Context
import androidx.room.Room
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.database.GlassPromptDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlassPromptDatabase =
        Room.databaseBuilder(context, GlassPromptDatabase::class.java, GlassPromptDatabase.NAME).build()

    @Provides
    fun provideProjectDao(database: GlassPromptDatabase): ProjectDao = database.projectDao()
}
