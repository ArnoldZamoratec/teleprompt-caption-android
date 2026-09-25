package com.arnoldcode.glassprompt.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    /** Denormalized so project lists never load script bodies. */
    val wordCount: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = ScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scriptId", unique = true), Index("updatedAt")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scriptId: String,
    @Embedded(prefix = "rec_") val recording: RecordingColumns,
    @Embedded(prefix = "tp_") val teleprompter: TeleprompterColumns,
    val captionStyleId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Enums are stored by name so reordering them never corrupts data. */
data class RecordingColumns(
    val lens: String,
    val orientation: String,
    val resolution: String,
    val frameRate: Int,
)

data class TeleprompterColumns(
    val speed: Float,
    val fontSizeSp: Float,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val horizontalMarginDp: Int,
    val position: String,
    val alignment: String,
    val mirror: Boolean,
    val backgroundOpacity: Float,
    val countdownSeconds: Int,
)

/** Projection for list screens. */
data class ProjectSummaryRow(
    val id: String,
    val name: String,
    val wordCount: Int,
    val updatedAt: Long,
)
