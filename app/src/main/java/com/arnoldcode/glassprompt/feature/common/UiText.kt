package com.arnoldcode.glassprompt.feature.common

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.common.AppError

/** Friendly, localized message for a domain error. Technical details never reach the user. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    is AppError.PermissionDenied -> R.string.error_permission
    is AppError.CameraUnavailable -> R.string.error_camera
    is AppError.RecordingFailed -> R.string.error_recording
    AppError.StorageFull -> R.string.error_storage_full
    is AppError.FileNotReadable -> R.string.error_file_not_readable
    AppError.FileTooLarge -> R.string.error_file_too_large
    AppError.EmptyContent -> R.string.error_empty_content
    is AppError.InvalidInput -> R.string.error_invalid_input
    is AppError.TranscriptionUnavailable -> R.string.error_transcription
    is AppError.ExportFailed -> R.string.error_export
    is AppError.NotFound -> R.string.error_not_found
    is AppError.Unexpected -> R.string.error_unexpected
}

/** "hace 5 min", "ayer"… in the device language; "Justo ahora" for the last minute. */
@Composable
fun relativeTimeLabel(timestamp: Long): String {
    val now = remember(timestamp) { System.currentTimeMillis() }
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.time_just_now)
    return remember(timestamp, now) {
        DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }
}

/** Reading-time label: "8 min 12 s" or "45 s". */
@Composable
fun durationLabel(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        stringResource(R.string.duration_min_sec, minutes, seconds)
    } else {
        stringResource(R.string.duration_sec, seconds)
    }
}
