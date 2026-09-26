package com.arnoldcode.glassprompt.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arnoldcode.glassprompt.domain.model.ExportedVideo

@Entity(
    tableName = "exports",
    foreignKeys = [
        ForeignKey(entity = TakeEntity::class, parentColumns = ["id"], childColumns = ["takeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("takeId"), Index("createdAt")],
)
data class ExportEntity(
    @PrimaryKey val id: String,
    val takeId: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val withCaptions: Boolean,
    val galleryUri: String?,
    val createdAt: Long,
)

/** An export with the project it belongs to (via its take). */
data class ExportWithProject(
    @Embedded val export: ExportEntity,
    val projectId: String,
    val projectName: String,
)

fun ExportWithProject.toDomain() = ExportedVideo(
    id = export.id,
    takeId = export.takeId,
    projectId = projectId,
    projectName = projectName,
    filePath = export.filePath,
    width = export.width,
    height = export.height,
    frameRate = export.frameRate,
    durationMs = export.durationMs,
    sizeBytes = export.sizeBytes,
    withCaptions = export.withCaptions,
    galleryUri = export.galleryUri,
    createdAt = export.createdAt,
)
