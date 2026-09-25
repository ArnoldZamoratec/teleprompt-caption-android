package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import com.arnoldcode.glassprompt.domain.project.ProjectNames
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.script.ScriptText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Project list filtered by an accent- and case-insensitive name query. */
class ObserveProjectsUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    operator fun invoke(query: String = ""): Flow<List<ProjectSummary>> {
        val key = ScriptText.searchKey(query.trim())
        return repository.observeProjects().map { projects ->
            if (key.isEmpty()) projects else projects.filter { key in ScriptText.searchKey(it.name) }
        }
    }
}

class ObserveRecentProjectsUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    operator fun invoke(limit: Int = 10): Flow<List<ProjectSummary>> =
        repository.observeProjects().map { it.take(limit) }
}

class ObserveProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    operator fun invoke(id: String): Flow<Project?> = repository.observeProject(id)
}

class GetProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Project> = repository.getProject(id)
}

/** Shared name rule: trimmed, non-blank and at most [Project.MAX_NAME_LENGTH] characters. */
internal fun validProjectName(raw: String): String? =
    raw.trim().takeIf { it.isNotEmpty() && it.length <= Project.MAX_NAME_LENGTH }

class CreateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(project: NewProject): AppResult<String> {
        val name = validProjectName(project.name) ?: return AppResult.Failure(AppError.InvalidInput(FIELD_NAME))
        return repository.createProject(project.copy(name = name, scriptBody = ScriptText.normalize(project.scriptBody)))
    }
}

class UpdateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(project: Project): AppResult<Unit> {
        val name = validProjectName(project.name) ?: return AppResult.Failure(AppError.InvalidInput(FIELD_NAME))
        return repository.updateProject(project.copy(name = name))
    }
}

class DuplicateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: String): AppResult<String> {
        val original = when (val result = repository.getProject(id)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val name = ProjectNames.copyName(original.name, repository.projectNames())
        return repository.duplicateProject(id, name)
    }
}

class DeleteProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.deleteProject(id)
}

const val FIELD_NAME = "name"
