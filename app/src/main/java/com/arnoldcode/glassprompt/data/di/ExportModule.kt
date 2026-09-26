package com.arnoldcode.glassprompt.data.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.arnoldcode.glassprompt.data.multimedia.export.Media3VideoExporter
import com.arnoldcode.glassprompt.data.storage.MediaStoreVideoGallery
import com.arnoldcode.glassprompt.data.storage.SrtSubtitleFiles
import com.arnoldcode.glassprompt.data.storage.VideoThumbnailStore
import com.arnoldcode.glassprompt.data.work.WorkManagerExportScheduler
import com.arnoldcode.glassprompt.domain.repository.ExportScheduler
import com.arnoldcode.glassprompt.domain.repository.SubtitleFiles
import com.arnoldcode.glassprompt.domain.repository.VideoExporter
import com.arnoldcode.glassprompt.domain.repository.VideoGallery
import com.arnoldcode.glassprompt.domain.repository.VideoThumbnails
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @OptIn(UnstableApi::class)
    @Binds
    abstract fun bindVideoExporter(impl: Media3VideoExporter): VideoExporter

    @Binds
    abstract fun bindVideoGallery(impl: MediaStoreVideoGallery): VideoGallery

    @Binds
    abstract fun bindSubtitleFiles(impl: SrtSubtitleFiles): SubtitleFiles

    @Binds
    abstract fun bindVideoThumbnails(impl: VideoThumbnailStore): VideoThumbnails
}

/** Separate so instrumented tests can swap WorkManager for a fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExportSchedulerModule {

    @Binds
    abstract fun bindExportScheduler(impl: WorkManagerExportScheduler): ExportScheduler
}
