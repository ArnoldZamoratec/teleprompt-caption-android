package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.data.repository.RoomProjectRepository
import com.arnoldcode.glassprompt.data.repository.RoomScriptRepository
import com.arnoldcode.glassprompt.data.storage.ContentResolverTextReader
import com.arnoldcode.glassprompt.data.templates.ResourceTemplateRepository
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
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
    abstract fun bindTextDocumentReader(impl: ContentResolverTextReader): TextDocumentReader
}
