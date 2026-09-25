package com.arnoldcode.glassprompt.core.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. Arguments are primitive IDs only. */
sealed interface Route {
    @Serializable data object Onboarding : Route
    @Serializable data object Main : Route

    // Top-level tabs hosted inside Main.
    @Serializable data object Home : Route
    @Serializable data object Projects : Route
    @Serializable data object Templates : Route
    @Serializable data object Settings : Route

    // Creation / recording flow.
    @Serializable data class ProjectSetup(val projectId: String? = null) : Route
    @Serializable data class ScriptEditor(val projectId: String) : Route
    @Serializable data class Teleprompter(val projectId: String) : Route
    @Serializable data class Camera(val projectId: String) : Route
    @Serializable data class VideoReview(val takeId: String) : Route
    @Serializable data class CaptionEditor(val takeId: String) : Route
    @Serializable data class Export(val takeId: String) : Route
    @Serializable data class ExportResult(val exportId: String) : Route

    @Serializable data object PrivacySettings : Route
}
