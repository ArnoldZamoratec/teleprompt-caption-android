package com.arnoldcode.glassprompt.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arnoldcode.glassprompt.domain.model.Take

@Entity(
    tableName = "takes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("createdAt")],
)
data class TakeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val filePath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val createdAt: Long,
)

fun TakeEntity.toDomain() = Take(
    id = id,
    projectId = projectId,
    filePath = filePath,
    durationMs = durationMs,
    width = width,
    height = height,
    frameRate = frameRate,
    createdAt = createdAt,
)
