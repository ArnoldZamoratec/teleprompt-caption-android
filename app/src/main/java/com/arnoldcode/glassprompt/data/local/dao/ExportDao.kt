package com.arnoldcode.glassprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arnoldcode.glassprompt.data.local.entities.ExportEntity
import com.arnoldcode.glassprompt.data.local.entities.ExportWithProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportDao {

    @Query(
        """
        SELECT e.*, p.id AS projectId, p.name AS projectName FROM exports e
        JOIN takes t ON t.id = e.takeId JOIN projects p ON p.id = t.projectId
        ORDER BY e.createdAt DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<ExportWithProject>>

    @Query(
        """
        SELECT e.*, p.id AS projectId, p.name AS projectName FROM exports e
        JOIN takes t ON t.id = e.takeId JOIN projects p ON p.id = t.projectId
        WHERE e.id = :id
        """,
    )
    fun observe(id: String): Flow<ExportWithProject?>

    @Query("SELECT * FROM exports WHERE id = :id")
    suspend fun get(id: String): ExportEntity?

    /** Files to delete with a take (rows go by cascade). */
    @Query("SELECT filePath FROM exports WHERE takeId = :takeId")
    suspend fun filePathsOfTake(takeId: String): List<String>

    /** Files to delete with a project (rows go by cascade). */
    @Query("SELECT e.filePath FROM exports e JOIN takes t ON t.id = e.takeId WHERE t.projectId = :projectId")
    suspend fun filePathsOfProject(projectId: String): List<String>

    @Insert
    suspend fun insert(export: ExportEntity)

    @Query("UPDATE exports SET galleryUri = :uri WHERE id = :id")
    suspend fun setGalleryUri(id: String, uri: String): Int

    @Query("DELETE FROM exports WHERE id = :id")
    suspend fun delete(id: String): Int
}
