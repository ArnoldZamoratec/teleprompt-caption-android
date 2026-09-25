package com.arnoldcode.glassprompt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.arnoldcode.glassprompt.data.local.entities.ProjectEntity
import com.arnoldcode.glassprompt.data.local.entities.ProjectSummaryRow
import com.arnoldcode.glassprompt.data.local.entities.ScriptEntity
import kotlinx.coroutines.flow.Flow

/**
 * Projects and their scripts. A project owns exactly one script, so multi-table writes live
 * here as transactions instead of being coordinated by the repositories.
 */
@Dao
abstract class ProjectDao {

    @Query(
        """
        SELECT p.id AS id, p.name AS name, s.wordCount AS wordCount, p.updatedAt AS updatedAt
        FROM projects p INNER JOIN scripts s ON s.id = p.scriptId
        ORDER BY p.updatedAt DESC
        """,
    )
    abstract fun observeSummaries(): Flow<List<ProjectSummaryRow>>

    @Query("SELECT * FROM projects WHERE id = :id")
    abstract fun observeProject(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    abstract suspend fun getProject(id: String): ProjectEntity?

    @Query("SELECT name FROM projects")
    abstract suspend fun projectNames(): List<String>

    @Update
    abstract suspend fun updateProject(project: ProjectEntity): Int

    @Query("SELECT * FROM scripts WHERE id = :id")
    abstract fun observeScript(id: String): Flow<ScriptEntity?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    abstract suspend fun getScript(id: String): ScriptEntity?

    @Insert
    protected abstract suspend fun insertScript(script: ScriptEntity)

    @Insert
    protected abstract suspend fun insertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    protected abstract suspend fun deleteProjectRow(id: String): Int

    @Query("DELETE FROM scripts WHERE id = :id")
    protected abstract suspend fun deleteScriptRow(id: String): Int

    @Query("UPDATE scripts SET body = :body, wordCount = :wordCount, updatedAt = :updatedAt WHERE id = :id")
    protected abstract suspend fun updateScriptRow(id: String, body: String, wordCount: Int, updatedAt: Long): Int

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE scriptId = :scriptId")
    protected abstract suspend fun touchProjectsOf(scriptId: String, updatedAt: Long)

    @Transaction
    open suspend fun insertProjectWithScript(project: ProjectEntity, script: ScriptEntity) {
        insertScript(script)
        insertProject(project)
    }

    /** Returns false when the script does not exist. */
    @Transaction
    open suspend fun saveScriptBody(id: String, body: String, wordCount: Int, updatedAt: Long): Boolean {
        val updated = updateScriptRow(id, body, wordCount, updatedAt) > 0
        if (updated) touchProjectsOf(id, updatedAt)
        return updated
    }

    /** Deletes the project and its script. Returns false when the project does not exist. */
    @Transaction
    open suspend fun deleteProjectWithScript(id: String): Boolean {
        val project = getProject(id) ?: return false
        deleteProjectRow(id)
        deleteScriptRow(project.scriptId)
        return true
    }
}
