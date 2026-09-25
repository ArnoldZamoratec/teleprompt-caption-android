package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.Script
import kotlinx.coroutines.flow.Flow

interface ScriptRepository {
    fun observeScript(id: String): Flow<Script?>
    suspend fun getScript(id: String): AppResult<Script>

    /** Saves the body and bumps the owning project's `updatedAt` so it surfaces as recent. */
    suspend fun saveBody(scriptId: String, body: String): AppResult<Unit>
}

/** Reads a user-picked text document (a content URI as string). Implemented with the platform in data. */
interface TextDocumentReader {
    /** Fails with `AppError.FileTooLarge` when the text exceeds [maxChars]. */
    suspend fun readText(uri: String, maxChars: Int): AppResult<String>
}
