package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.model.Script
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
import com.arnoldcode.glassprompt.domain.script.ScriptText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory projects + scripts with a manual clock. Set [failWith] to make writes fail. */
class FakeProjectRepository : ProjectRepository, ScriptRepository {

    private val projects = MutableStateFlow<Map<String, Project>>(emptyMap())
    private val scripts = MutableStateFlow<Map<String, Script>>(emptyMap())
    private var nextId = 1

    var now: Long = 1_000L
    var failWith: AppError? = null
    val savedBodies = mutableListOf<String>()

    fun script(id: String): Script? = scripts.value[id]
    fun project(id: String): Project? = projects.value[id]

    /** Seeds a project directly; returns it. */
    fun seed(name: String, body: String = "", updatedAt: Long = now): Project {
        val scriptId = "s${nextId++}"
        val project = Project(id = "p${nextId++}", name = name, scriptId = scriptId, createdAt = updatedAt, updatedAt = updatedAt)
        scripts.update { it + (scriptId to Script(scriptId, name, body, updatedAt)) }
        projects.update { it + (project.id to project) }
        return project
    }

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        projects.map { all ->
            all.values.sortedByDescending { it.updatedAt }.map { p ->
                ProjectSummary(p.id, p.name, ScriptText.countWords(scripts.value[p.scriptId]?.body.orEmpty()), p.updatedAt)
            }
        }

    override fun observeProject(id: String): Flow<Project?> = projects.map { it[id] }

    override suspend fun getProject(id: String): AppResult<Project> =
        projects.value[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound(id))

    override suspend fun projectNames(): List<String> = projects.value.values.map { it.name }

    override suspend fun createProject(project: NewProject): AppResult<String> {
        failWith?.let { return AppResult.Failure(it) }
        val scriptId = "s${nextId++}"
        val created = Project(
            id = "p${nextId++}",
            name = project.name,
            scriptId = scriptId,
            recording = project.recording,
            teleprompter = project.teleprompter,
            createdAt = now,
            updatedAt = now,
        )
        scripts.update { it + (scriptId to Script(scriptId, project.name, project.scriptBody, now)) }
        projects.update { it + (created.id to created) }
        return AppResult.Success(created.id)
    }

    override suspend fun updateProject(project: Project): AppResult<Unit> {
        failWith?.let { return AppResult.Failure(it) }
        if (project.id !in projects.value) return AppResult.Failure(AppError.NotFound(project.id))
        projects.update { it + (project.id to project.copy(updatedAt = now)) }
        return AppResult.Success(Unit)
    }

    override suspend fun duplicateProject(id: String, newName: String): AppResult<String> {
        failWith?.let { return AppResult.Failure(it) }
        val original = projects.value[id] ?: return AppResult.Failure(AppError.NotFound(id))
        val body = scripts.value[original.scriptId]?.body.orEmpty()
        return createProject(NewProject(newName, original.recording, original.teleprompter, body))
    }

    override suspend fun deleteProject(id: String): AppResult<Unit> {
        failWith?.let { return AppResult.Failure(it) }
        val project = projects.value[id] ?: return AppResult.Failure(AppError.NotFound(id))
        projects.update { it - id }
        scripts.update { it - project.scriptId }
        return AppResult.Success(Unit)
    }

    override fun observeScript(id: String): Flow<Script?> = scripts.map { it[id] }

    override suspend fun getScript(id: String): AppResult<Script> =
        scripts.value[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound(id))

    override suspend fun saveBody(scriptId: String, body: String): AppResult<Unit> {
        failWith?.let { return AppResult.Failure(it) }
        val script = scripts.value[scriptId] ?: return AppResult.Failure(AppError.NotFound(scriptId))
        savedBodies += body
        scripts.update { it + (scriptId to script.copy(body = body, updatedAt = now)) }
        projects.update { all -> all.mapValues { (_, p) -> if (p.scriptId == scriptId) p.copy(updatedAt = now) else p } }
        return AppResult.Success(Unit)
    }
}
