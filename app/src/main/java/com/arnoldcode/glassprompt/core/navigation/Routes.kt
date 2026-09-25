package com.arnoldcode.glassprompt.core.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. Arguments are primitive IDs only. */
sealed interface Route {
    @Serializable data object Onboarding : Route

    // Top-level tabs (bottom bar / navigation rail).
    @Serializable data object Home : Route
    @Serializable data object Projects : Route
    @Serializable data object Templates : Route
    @Serializable data object Settings : Route

    // Creation / recording flow.
    /** Creates a project (optionally from [templateId]) or edits [projectId]. */
    @Serializable data class ProjectSetup(
        val projectId: String? = null,
        val templateId: String? = null,
        val importScript: Boolean = false,
    ) : Route

    /** [launchImport] opens the document picker as soon as the editor is ready. */
    @Serializable data class ScriptEditor(val projectId: String, val launchImport: Boolean = false) : Route
    @Serializable data class Teleprompter(val projectId: String) : Route
    @Serializable data class Camera(val projectId: String) : Route
    @Serializable data class VideoReview(val takeId: String) : Route
    @Serializable data class CaptionEditor(val takeId: String) : Route
    @Serializable data class Export(val takeId: String) : Route
    @Serializable data class ExportResult(val exportId: String) : Route

    @Serializable data object PrivacySettings : Route
}
