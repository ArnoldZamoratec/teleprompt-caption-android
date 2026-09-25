package com.arnoldcode.glassprompt.feature.projects

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.domain.usecase.DeleteProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.DuplicateProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveProjectsUseCase
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ProjectsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProjectRepository()
    private val viewModel = ProjectsViewModel(
        ObserveProjectsUseCase(repository),
        DuplicateProjectUseCase(repository),
        DeleteProjectUseCase(repository),
    )

    /** Collects until [predicate] holds; the first emission is always the loading state. */
    private suspend fun ReceiveTurbine<ProjectsUiState>.awaitUntil(predicate: (ProjectsUiState) -> Boolean): ProjectsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `lists projects and filters by query`() = runTest {
        repository.seed("Tutorial Kotlin", updatedAt = 2)
        repository.seed("Reseña cámara", updatedAt = 1)

        viewModel.uiState.test {
            val loaded = awaitUntil { it.projects != null }
            assertThat(loaded.projects?.map { it.name }).containsExactly("Tutorial Kotlin", "Reseña cámara").inOrder()

            viewModel.onQueryChange("resena")
            val filtered = awaitUntil { it.query == "resena" && it.projects?.size == 1 }
            assertThat(filtered.projects?.single()?.name).isEqualTo("Reseña cámara")
        }
    }

    @Test
    fun `delete requires confirmation`() = runTest {
        val project = repository.seed("Borrar")

        viewModel.uiState.test {
            val summary = awaitUntil { it.projects != null }.projects!!.single()
            viewModel.onDeleteRequest(summary)
            assertThat(awaitUntil { it.pendingDelete != null }.pendingDelete).isEqualTo(summary)

            viewModel.onDeleteConfirm()
            val state = awaitUntil { it.projects.isNullOrEmpty() && it.message != null }
            assertThat(state.pendingDelete).isNull()
            assertThat(state.message).isEqualTo(R.string.projects_deleted)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.project(project.id)).isNull()
    }

    @Test
    fun `dismissing delete keeps the project`() = runTest {
        repository.seed("Quedarse")
        viewModel.uiState.test {
            viewModel.onDeleteRequest(awaitUntil { it.projects != null }.projects!!.single())
            awaitUntil { it.pendingDelete != null }
            viewModel.onDeleteDismiss()
            val state = awaitUntil { it.pendingDelete == null }
            assertThat(state.projects).hasSize(1)
        }
    }

    @Test
    fun `duplicate adds a numbered copy`() = runTest {
        val project = repository.seed("Demo")
        viewModel.uiState.test {
            awaitUntil { it.projects != null }
            viewModel.onDuplicate(project.id)
            val state = awaitUntil { it.projects?.size == 2 && it.message != null }
            assertThat(state.projects?.map { it.name }).contains("Demo (2)")
            assertThat(state.message).isEqualTo(R.string.projects_duplicated)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
