package com.arnoldcode.glassprompt.data.di

import android.content.Context
import androidx.room.Room
import com.arnoldcode.glassprompt.data.local.dao.CaptionDao
import com.arnoldcode.glassprompt.data.local.dao.ExportDao
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.dao.TakeDao
import com.arnoldcode.glassprompt.data.local.database.GlassPromptDatabase
import com.arnoldcode.glassprompt.data.local.database.Migrations
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
        Room.databaseBuilder(context, GlassPromptDatabase::class.java, GlassPromptDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideProjectDao(database: GlassPromptDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideTakeDao(database: GlassPromptDatabase): TakeDao = database.takeDao()

    @Provides
    fun provideCaptionDao(database: GlassPromptDatabase): CaptionDao = database.captionDao()

    @Provides
    fun provideExportDao(database: GlassPromptDatabase): ExportDao = database.exportDao()
}
