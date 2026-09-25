package com.arnoldcode.glassprompt.data.storage

import android.content.Context
import androidx.core.net.toUri
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.domain.repository.TextDocumentReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a document picked through the Storage Access Framework. Streams at most
 * `maxChars + 1` characters so a huge file is rejected without loading it into memory.
 */
@Singleton
class ContentResolverTextReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : TextDocumentReader {

    override suspend fun readText(uri: String, maxChars: Int): AppResult<String> = withContext(dispatchers.io) {
        safeCall(logger, TAG, mapError = { if (it is TooLarge) AppError.FileTooLarge else AppError.FileNotReadable(it) }) {
            val stream = context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri)
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            InputStreamReader(stream, decoder).use { reader ->
                val buffer = CharArray(8 * 1024)
                val text = StringBuilder()
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.appendRange(buffer, 0, read)
                    if (text.length > maxChars) throw TooLarge()
                }
                text.toString()
            }
        }
    }

    private class TooLarge : RuntimeException()

    private companion object {
        const val TAG = "TextReader"
    }
}
