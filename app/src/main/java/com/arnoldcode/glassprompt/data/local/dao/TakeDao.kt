package com.arnoldcode.glassprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arnoldcode.glassprompt.data.local.entities.TakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TakeDao {

    @Query("SELECT * FROM takes WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<TakeEntity>>

    @Query("SELECT * FROM takes WHERE id = :id")
    fun observe(id: String): Flow<TakeEntity?>

    @Query("SELECT * FROM takes WHERE id = :id")
    suspend fun get(id: String): TakeEntity?

    @Query("SELECT filePath FROM takes WHERE projectId = :projectId")
    suspend fun filePathsOf(projectId: String): List<String>

    @Insert
    suspend fun insert(take: TakeEntity)

    @Query("DELETE FROM takes WHERE id = :id")
    suspend fun delete(id: String): Int
}
