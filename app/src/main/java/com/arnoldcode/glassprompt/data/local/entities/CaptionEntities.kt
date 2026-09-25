package com.arnoldcode.glassprompt.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One row per captioned take: the style and where the captions came from. Its captions live
 * in [CaptionEntity]. Presets are code ([com.arnoldcode.glassprompt.domain.model.CaptionStyles]),
 * so the track stores the resolved style, not a reference to a style table.
 */
@Entity(
    tableName = "caption_tracks",
    foreignKeys = [
        ForeignKey(entity = TakeEntity::class, parentColumns = ["id"], childColumns = ["takeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class CaptionTrackEntity(
    @PrimaryKey val takeId: String,
    /** [CaptionStyle] as JSON: new style fields get their defaults instead of needing a migration. */
    val styleJson: String,
    val language: String,
    val engineId: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "captions",
    foreignKeys = [
        ForeignKey(entity = CaptionTrackEntity::class, parentColumns = ["takeId"], childColumns = ["takeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("takeId")],
)
data class CaptionEntity(
    @PrimaryKey val id: String,
    val takeId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    /** [CaptionWord] list as JSON. */
    val wordsJson: String,
)

internal object CaptionJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val wordsSerializer = ListSerializer(CaptionWord.serializer())

    fun encodeStyle(style: CaptionStyle): String = json.encodeToString(CaptionStyle.serializer(), style)

    /** Unreadable JSON (e.g. a preset removed in a later version) falls back to the default style. */
    fun decodeStyle(value: String): CaptionStyle =
        runCatching { json.decodeFromString(CaptionStyle.serializer(), value) }.getOrElse { CaptionStyle() }

    fun encodeWords(words: List<CaptionWord>): String = json.encodeToString(wordsSerializer, words)

    fun decodeWords(value: String): List<CaptionWord> =
        runCatching { json.decodeFromString(wordsSerializer, value) }.getOrElse { emptyList() }
}

fun CaptionEntity.toDomain() = Caption(
    id = id,
    text = text,
    startMs = startMs,
    endMs = endMs,
    words = CaptionJson.decodeWords(wordsJson),
)

fun Caption.toEntity(takeId: String) = CaptionEntity(
    id = id,
    takeId = takeId,
    text = text,
    startMs = startMs,
    endMs = endMs,
    wordsJson = CaptionJson.encodeWords(words),
)
