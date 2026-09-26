package com.arnoldcode.glassprompt.data.storage

import android.content.Context
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.domain.captions.SrtWriter
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import com.arnoldcode.glassprompt.domain.repository.SubtitleFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** .srt files in cacheDir/subtitles (shared through the FileProvider, cleared by the system when needed). */
class SrtSubtitleFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val captions: CaptionRepository,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : SubtitleFiles {

    override suspend fun writeSrt(takeId: String, baseName: String): AppResult<String> = withContext(dispatchers.io) {
        val track = captions.getTrack(takeId)?.takeIf { it.captions.isNotEmpty() }
            ?: return@withContext AppResult.Failure(AppError.EmptyContent)
        safeCall(logger, TAG) {
            val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
            val file = File(dir, "$baseName.srt")
            file.writeText(SrtWriter.write(track.captions))
            file.absolutePath
        }
    }

    private companion object {
        const val TAG = "Subtitles"
    }
}
