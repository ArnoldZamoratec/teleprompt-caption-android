package com.arnoldcode.glassprompt.data.local.entities

import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.model.RecordingSettings
import com.arnoldcode.glassprompt.domain.model.Script
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings

/** Unknown stored names (e.g. from a newer app version) fall back to the default instead of crashing. */
private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    scriptId = scriptId,
    recording = recording.toDomain(),
    teleprompter = teleprompter.toDomain(),
    captionStyleId = captionStyleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    scriptId = scriptId,
    recording = recording.toColumns(),
    teleprompter = teleprompter.toColumns(),
    captionStyleId = captionStyleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecordingColumns.toDomain(): RecordingSettings {
    val defaults = RecordingSettings()
    return RecordingSettings(
        lens = enumOr(lens, defaults.lens),
        orientation = enumOr(orientation, defaults.orientation),
        resolution = enumOr(resolution, defaults.resolution),
        frameRate = frameRate,
    )
}

fun RecordingSettings.toColumns() = RecordingColumns(
    lens = lens.name,
    orientation = orientation.name,
    resolution = resolution.name,
    frameRate = frameRate,
)

fun TeleprompterColumns.toDomain(): TeleprompterSettings {
    val defaults = TeleprompterSettings()
    return TeleprompterSettings(
        speed = speed,
        fontSizeSp = fontSizeSp,
        lineSpacing = lineSpacing,
        letterSpacing = letterSpacing,
        horizontalMarginDp = horizontalMarginDp,
        position = enumOr(position, defaults.position),
        alignment = enumOr(alignment, defaults.alignment),
        mirror = mirror,
        backgroundOpacity = backgroundOpacity,
        countdownSeconds = countdownSeconds,
    )
}

fun TeleprompterSettings.toColumns() = TeleprompterColumns(
    speed = speed,
    fontSizeSp = fontSizeSp,
    lineSpacing = lineSpacing,
    letterSpacing = letterSpacing,
    horizontalMarginDp = horizontalMarginDp,
    position = position.name,
    alignment = alignment.name,
    mirror = mirror,
    backgroundOpacity = backgroundOpacity,
    countdownSeconds = countdownSeconds,
)

fun ScriptEntity.toDomain() = Script(id = id, title = title, body = body, updatedAt = updatedAt)

fun ProjectSummaryRow.toDomain() = ProjectSummary(id = id, name = name, wordCount = wordCount, updatedAt = updatedAt)

