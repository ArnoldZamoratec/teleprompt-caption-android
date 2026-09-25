package com.arnoldcode.glassprompt.domain.repository

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.ProjectSummary
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    /** All projects, most recently updated first. */
    fun observeProjects(): Flow<List<ProjectSummary>>
    fun observeProject(id: String): Flow<Project?>
    suspend fun getProject(id: String): AppResult<Project>
    suspend fun projectNames(): List<String>

    /** Creates the project and its script atomically; returns the new project ID. */
    suspend fun createProject(project: NewProject): AppResult<String>
    suspend fun updateProject(project: Project): AppResult<Unit>

    /** Copies the project and its script under [newName]; returns the copy's ID. */
    suspend fun duplicateProject(id: String, newName: String): AppResult<String>
    suspend fun deleteProject(id: String): AppResult<Unit>
}
