package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.data.repository.RoomCaptionRepository
import com.arnoldcode.glassprompt.data.repository.RoomExportRepository
import com.arnoldcode.glassprompt.data.repository.RoomProjectRepository
import com.arnoldcode.glassprompt.data.repository.RoomScriptRepository
import com.arnoldcode.glassprompt.data.repository.RoomTakeRepository
import com.arnoldcode.glassprompt.data.storage.MediaFileStore
import com.arnoldcode.glassprompt.data.storage.ContentResolverTextReader
import com.arnoldcode.glassprompt.data.templates.ResourceTemplateRepository
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import com.arnoldcode.glassprompt.domain.repository.ExportRepository
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import com.arnoldcode.glassprompt.domain.repository.TemplateRepository
import com.arnoldcode.glassprompt.domain.repository.TextDocumentReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindProjectRepository(impl: RoomProjectRepository): ProjectRepository

    @Binds
    abstract fun bindScriptRepository(impl: RoomScriptRepository): ScriptRepository

    @Binds
    abstract fun bindTemplateRepository(impl: ResourceTemplateRepository): TemplateRepository

    @Binds
    abstract fun bindTakeRepository(impl: RoomTakeRepository): TakeRepository

    @Binds
    abstract fun bindCaptionRepository(impl: RoomCaptionRepository): CaptionRepository

    @Binds
    abstract fun bindExportRepository(impl: RoomExportRepository): ExportRepository

    @Binds
    abstract fun bindMediaStorage(impl: MediaFileStore): MediaStorage

    @Binds
    abstract fun bindTextDocumentReader(impl: ContentResolverTextReader): TextDocumentReader
}
