package com.arnoldcode.glassprompt.domain.model

enum class TemplateCategory { TUTORIAL, REVIEW, AD, INTRO, STORY, EDUCATIONAL }

/** Ready-made script structure the user can start a project from. */
data class ScriptTemplate(
    val id: String,
    val category: TemplateCategory,
    val title: String,
    val description: String,
    val body: String,
)
