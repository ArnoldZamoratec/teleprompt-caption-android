package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.ScriptTemplate
import com.arnoldcode.glassprompt.domain.model.TemplateCategory
import com.arnoldcode.glassprompt.domain.repository.TemplateRepository
import com.arnoldcode.glassprompt.domain.repository.TextDocumentReader

class FakeTemplateRepository(
    private val items: List<ScriptTemplate> = listOf(
        ScriptTemplate("tutorial", TemplateCategory.TUTORIAL, "Tutorial", "Paso a paso", "Paso uno. Paso dos."),
        ScriptTemplate("review", TemplateCategory.REVIEW, "Reseña", "Pros y contras", "Lo bueno y lo malo."),
        ScriptTemplate("tips", TemplateCategory.EDUCATIONAL, "Consejos", "Rápidos", "Uno, dos, tres."),
    ),
) : TemplateRepository {
    override fun templates(): List<ScriptTemplate> = items
    override fun template(id: String): ScriptTemplate? = items.firstOrNull { it.id == id }
}

/** Serves [documents] by URI; unknown URIs fail as unreadable. */
class FakeTextDocumentReader(
    private val documents: Map<String, String> = emptyMap(),
) : TextDocumentReader {
    override suspend fun readText(uri: String, maxChars: Int): AppResult<String> {
        val text = documents[uri] ?: return AppResult.Failure(AppError.FileNotReadable())
        return if (text.length > maxChars) AppResult.Failure(AppError.FileTooLarge) else AppResult.Success(text)
    }
}
