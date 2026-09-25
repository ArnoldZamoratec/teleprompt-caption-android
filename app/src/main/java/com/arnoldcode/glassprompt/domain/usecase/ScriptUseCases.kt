package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.Script
import com.arnoldcode.glassprompt.domain.model.ScriptTemplate
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
import com.arnoldcode.glassprompt.domain.repository.TemplateRepository
import com.arnoldcode.glassprompt.domain.repository.TextDocumentReader
import com.arnoldcode.glassprompt.domain.script.ScriptStats
import com.arnoldcode.glassprompt.domain.script.ScriptText
import javax.inject.Inject

class GetScriptUseCase @Inject constructor(
    private val repository: ScriptRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Script> = repository.getScript(id)
}

/** Persists the script body. The editor debounces calls (autosave); this saves immediately. */
class SaveScriptUseCase @Inject constructor(
    private val repository: ScriptRepository,
) {
    suspend operator fun invoke(scriptId: String, body: String): AppResult<Unit> =
        repository.saveBody(scriptId, body)
}

/** Turns an imported `.txt` file or clipboard text into clean script text. */
class ImportScriptUseCase @Inject constructor(
    private val reader: TextDocumentReader,
) {
    suspend fun fromDocument(uri: String): AppResult<String> =
        when (val result = reader.readText(uri, ScriptText.MAX_IMPORT_CHARS)) {
            is AppResult.Success -> clean(result.data)
            is AppResult.Failure -> result
        }

    fun fromClipboard(text: String?): AppResult<String> = clean(text.orEmpty())

    private fun clean(raw: String): AppResult<String> {
        if (raw.length > ScriptText.MAX_IMPORT_CHARS) return AppResult.Failure(AppError.FileTooLarge)
        val text = ScriptText.normalize(raw)
        return if (text.isBlank()) AppResult.Failure(AppError.EmptyContent) else AppResult.Success(text)
    }
}

class ComputeScriptStatsUseCase @Inject constructor() {
    operator fun invoke(text: String, speed: Float = 1f): ScriptStats = ScriptStats.of(text, speed)
}

class GetTemplatesUseCase @Inject constructor(
    private val repository: TemplateRepository,
) {
    operator fun invoke(): List<ScriptTemplate> = repository.templates()

    fun byId(id: String): AppResult<ScriptTemplate> =
        repository.template(id)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("template $id"))
}
