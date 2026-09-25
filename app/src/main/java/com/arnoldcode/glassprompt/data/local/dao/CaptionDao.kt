package com.arnoldcode.glassprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.arnoldcode.glassprompt.data.local.entities.CaptionEntity
import com.arnoldcode.glassprompt.data.local.entities.CaptionTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionDao {

    @Query("SELECT * FROM caption_tracks WHERE takeId = :takeId")
    fun observeTrack(takeId: String): Flow<CaptionTrackEntity?>

    @Query("SELECT * FROM captions WHERE takeId = :takeId ORDER BY startMs")
    fun observeCaptions(takeId: String): Flow<List<CaptionEntity>>

    @Query("SELECT * FROM caption_tracks WHERE takeId = :takeId")
    suspend fun getTrack(takeId: String): CaptionTrackEntity?

    @Query("SELECT * FROM captions WHERE takeId = :takeId ORDER BY startMs")
    suspend fun getCaptions(takeId: String): List<CaptionEntity>

    @Upsert
    suspend fun upsertTrack(track: CaptionTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaptions(captions: List<CaptionEntity>)

    @Query("DELETE FROM captions WHERE takeId = :takeId")
    suspend fun deleteCaptions(takeId: String)

    @Query("UPDATE caption_tracks SET styleJson = :styleJson, updatedAt = :updatedAt WHERE takeId = :takeId")
    suspend fun updateStyle(takeId: String, styleJson: String, updatedAt: Long): Int

    @Query("DELETE FROM caption_tracks WHERE takeId = :takeId")
    suspend fun deleteTrack(takeId: String)

    /** Replaces the whole track atomically, so readers never see half of an edit. */
    @Transaction
    suspend fun replaceTrack(track: CaptionTrackEntity, captions: List<CaptionEntity>) {
        upsertTrack(track)
        deleteCaptions(track.takeId)
        insertCaptions(captions)
    }
}
