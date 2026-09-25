package com.arnoldcode.glassprompt.core.common

/** Domain-level errors. Each one maps to a friendly, localized message in the UI layer. */
sealed interface AppError {
    val cause: Throwable? get() = null

    data class PermissionDenied(val permission: String) : AppError
    data class CameraUnavailable(override val cause: Throwable? = null) : AppError
    data class RecordingFailed(override val cause: Throwable? = null) : AppError
    data object StorageFull : AppError
    data class FileNotReadable(override val cause: Throwable? = null) : AppError
    data object FileTooLarge : AppError
    data object EmptyContent : AppError
    data class InvalidInput(val field: String) : AppError
    data class TranscriptionUnavailable(override val cause: Throwable? = null) : AppError
    data class ExportFailed(override val cause: Throwable? = null) : AppError
    data class NotFound(val what: String) : AppError
    data class Unexpected(override val cause: Throwable? = null) : AppError
}
