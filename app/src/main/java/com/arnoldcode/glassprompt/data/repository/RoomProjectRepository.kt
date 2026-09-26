package com.arnoldcode.glassprompt.data.repository

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.safeCall
import com.arnoldcode.glassprompt.data.local.dao.ExportDao
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.dao.TakeDao
import com.arnoldcode.glassprompt.data.local.entities.ProjectEntity
import com.arnoldcode.glassprompt.data.local.entities.ScriptEntity
import com.arnoldcode.glassprompt.data.local.entities.toColumns
import com.arnoldcode.glassprompt.data.local.entities.toDomain
import com.arnoldcode.glassprompt.data.local.entities.toEntity
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.model.Script
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
import com.arnoldcode.glassprompt.domain.script.ScriptText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val takeDao: TakeDao,
    private val exportDao: ExportDao,
    private val storage: MediaStorage,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val logger: Logger,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeProject(id: String): Flow<Project?> =
        dao.observeProject(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getProject(id: String): AppResult<Project> = result {
        dao.getProject(id)?.toDomain() ?: throw NotFound("project $id")
    }

    override suspend fun projectNames(): List<String> =
        (result { dao.projectNames() } as? AppResult.Success)?.data.orEmpty()

    override suspend fun createProject(project: NewProject): AppResult<String> = result {
        val now = clock.millis()
        val script = ScriptEntity(
            id = ids.newId(),
            title = project.name,
            body = project.scriptBody,
            wordCount = ScriptText.countWords(project.scriptBody),
            updatedAt = now,
        )
        val entity = ProjectEntity(
            id = ids.newId(),
            name = project.name,
            scriptId = script.id,
            recording = project.recording.toColumns(),
            teleprompter = project.teleprompter.toColumns(),
            captionStyleId = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertProjectWithScript(entity, script)
        entity.id
    }

    override suspend fun updateProject(project: Project): AppResult<Unit> = result {
        val updated = dao.updateProject(project.copy(updatedAt = clock.millis()).toEntity())
        if (updated == 0) throw NotFound("project ${project.id}")
    }

    override suspend fun duplicateProject(id: String, newName: String): AppResult<String> = result {
        val original = dao.getProject(id) ?: throw NotFound("project $id")
        val script = dao.getScript(original.scriptId) ?: throw NotFound("script ${original.scriptId}")
        val now = clock.millis()
        val scriptCopy = script.copy(id = ids.newId(), title = newName, updatedAt = now)
        val copy = original.copy(id = ids.newId(), name = newName, scriptId = scriptCopy.id, createdAt = now, updatedAt = now)
        dao.insertProjectWithScript(copy, scriptCopy)
        copy.id
    }

    /** Rows cascade (takes → project); video files are removed once the rows are gone. */
    override suspend fun deleteProject(id: String): AppResult<Unit> = result {
        // Rows go by cascade; the files have to be deleted by hand.
        val files = takeDao.filePathsOf(id) + exportDao.filePathsOfProject(id)
        if (!dao.deleteProjectWithScript(id)) throw NotFound("project $id")
        files.forEach(storage::delete)
    }

    private inline fun <T> result(block: () -> T): AppResult<T> =
        safeCall(logger, TAG, mapError = ::toAppError, block = block)

    private companion object {
        const val TAG = "Projects"
    }
}

@Singleton
class RoomScriptRepository @Inject constructor(
    private val dao: ProjectDao,
    private val clock: Clock,
    private val logger: Logger,
) : ScriptRepository {

    override fun observeScript(id: String): Flow<Script?> =
        dao.observeScript(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getScript(id: String): AppResult<Script> =
        safeCall(logger, TAG, ::toAppError) { dao.getScript(id)?.toDomain() ?: throw NotFound("script $id") }

    override suspend fun saveBody(scriptId: String, body: String): AppResult<Unit> =
        safeCall(logger, TAG, ::toAppError) {
            val saved = dao.saveScriptBody(scriptId, body, ScriptText.countWords(body), clock.millis())
            if (!saved) throw NotFound("script $scriptId")
        }

    private companion object {
        const val TAG = "Scripts"
    }
}

/** Internal signal for "row missing"; mapped to [AppError.NotFound] at the repository boundary. */
private class NotFound(val what: String) : RuntimeException(what)

private fun toAppError(error: Throwable): AppError = when (error) {
    is NotFound -> AppError.NotFound(error.what)
    is android.database.sqlite.SQLiteFullException -> AppError.StorageFull
    else -> AppError.Unexpected(error)
}
