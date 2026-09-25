package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.data.multimedia.audio.MediaCodecAudioExtractor
import com.arnoldcode.glassprompt.data.multimedia.transcription.AndroidSpeechEngine
import com.arnoldcode.glassprompt.data.work.WorkManagerTranscriptionScheduler
import com.arnoldcode.glassprompt.domain.repository.TranscriptionScheduler
import com.arnoldcode.glassprompt.domain.transcription.AudioExtractor
import com.arnoldcode.glassprompt.domain.transcription.ScriptAlignmentEngine
import com.arnoldcode.glassprompt.domain.transcription.SpeechModels
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionModule {

    @Binds
    abstract fun bindAudioExtractor(impl: MediaCodecAudioExtractor): AudioExtractor

    @Binds
    abstract fun bindSpeechModels(impl: AndroidSpeechEngine): SpeechModels

    companion object {
        /** Engines in order of preference. The script alignment always works, so it goes last. */
        @Provides
        fun provideEngines(
            speech: AndroidSpeechEngine,
            script: ScriptAlignmentEngine,
        ): List<@JvmSuppressWildcards TranscriptionEngine> = listOf(speech, script)
    }
}

/** Separate so instrumented tests can swap WorkManager for a fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionSchedulerModule {

    @Binds
    abstract fun bindTranscriptionScheduler(impl: WorkManagerTranscriptionScheduler): TranscriptionScheduler
}
