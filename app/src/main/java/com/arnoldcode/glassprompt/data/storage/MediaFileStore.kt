package com.arnoldcode.glassprompt.data.storage

import android.content.Context
import android.os.storage.StorageManager
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layout of app-private media: `filesDir/takes` (recordings), `filesDir/exports` (rendered
 * videos) and `cacheDir/audio` (temporary PCM). No storage permission is needed.
 */
@Singleton
class MediaFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ids: IdGenerator,
    private val logger: Logger,
) : MediaStorage {

    val takesDir: File get() = File(context.filesDir, "takes").apply { mkdirs() }
    val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }
    val audioCacheDir: File get() = File(context.cacheDir, "audio").apply { mkdirs() }

    override fun newTakeFile(): String = File(takesDir, "take_${ids.newId()}.mp4").absolutePath

    fun newExportFile(): String = File(exportsDir, "glassprompt_${ids.newId()}.mp4").absolutePath

    /** Free space including cache the system may clear for us, like the OS itself counts it. */
    override fun availableBytes(): Long = runCatching {
        val storage = context.getSystemService(StorageManager::class.java)
        storage.getAllocatableBytes(storage.getUuidForPath(context.filesDir))
    }.getOrElse { context.filesDir.usableSpace }

    override fun delete(path: String): Boolean {
        val file = File(path)
        // Never delete outside our own directories, whatever path the database holds.
        val inside = listOf(context.filesDir, context.cacheDir).any { file.canonicalPath.startsWith(it.canonicalPath) }
        if (!inside) {
            logger.w(TAG, "Refusing to delete a file outside app storage")
            return false
        }
        return !file.exists() || file.delete()
    }

    private companion object {
        const val TAG = "MediaFileStore"
    }
}
