package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.domain.model.ScriptTemplate

/** Built-in script templates (bundled, localized content). */
interface TemplateRepository {
    fun templates(): List<ScriptTemplate>
    fun template(id: String): ScriptTemplate?
}
