package com.arnoldcode.glassprompt.domain.model

/** One recorded video of a project. The file lives in app-internal storage. */
data class Take(
    val id: String,
    val projectId: String,
    val filePath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val createdAt: Long,
)

/** Data needed to register a finished recording. */
data class NewTake(
    val projectId: String,
    val filePath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
)

/** Lifecycle of a camera recording, derived from the camera's events. */
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Countdown(val secondsLeft: Int) : RecordingState
    data class Recording(val elapsedMs: Long) : RecordingState
    data class Paused(val elapsedMs: Long) : RecordingState
    data object Finalizing : RecordingState
}
