package com.arnoldcode.glassprompt.data.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.domain.repository.VideoGallery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Copies exports to Movies/GlassPrompt. Android 10+ goes through MediaStore (no permission, the
 * entry stays hidden until the copy is complete); Android 8–9 writes the file directly, which
 * needs WRITE_EXTERNAL_STORAGE (requested by the UI), and then asks the media scanner to index it.
 */
class MediaStoreVideoGallery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : VideoGallery {

    override suspend fun save(filePath: String, displayName: String): AppResult<String> = withContext(dispatchers.io) {
        val source = File(filePath)
        if (!source.exists()) return@withContext AppResult.Failure(AppError.NotFound(filePath))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            safeCall(logger, TAG, mapError = ::toError) { saveScoped(source, displayName) }
        } else {
            safeCall(logger, TAG, mapError = ::toError) { saveLegacy(source, displayName) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(source: File, displayName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Cannot open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(source: File, displayName: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM).apply { mkdirs() }
        val target = File(dir, displayName)
        source.copyTo(target, overwrite = true)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4")) { _, uri ->
                if (cont.isActive) cont.resume((uri ?: android.net.Uri.fromFile(target)).toString())
            }
        }
    }

    private fun toError(error: Throwable): AppError = when (error) {
        is SecurityException -> AppError.PermissionDenied("WRITE_EXTERNAL_STORAGE")
        is IOException -> if (error.message?.contains("ENOSPC") == true) AppError.StorageFull else AppError.FileNotReadable(error)
        else -> AppError.Unexpected(error)
    }

    private companion object {
        const val TAG = "Gallery"
        const val ALBUM = "GlassPrompt"
    }
}
