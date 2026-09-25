package com.arnoldcode.glassprompt.domain.model

/** A recording project: one script plus the camera and teleprompter setup used to record it. */
data class Project(
    val id: String,
    val name: String,
    val scriptId: String,
    val recording: RecordingSettings = RecordingSettings(),
    val teleprompter: TeleprompterSettings = TeleprompterSettings(),
    val captionStyleId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

/** Lightweight row for project lists; avoids loading script bodies. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val wordCount: Int,
    val updatedAt: Long,
)

/** Everything needed to create a project in one transaction. */
data class NewProject(
    val name: String,
    val recording: RecordingSettings = RecordingSettings(),
    val teleprompter: TeleprompterSettings = TeleprompterSettings(),
    val scriptBody: String = "",
)
