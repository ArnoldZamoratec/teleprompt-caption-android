package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewProject
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTextDocumentReader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProjectUseCasesTest {

    private val repository = FakeProjectRepository()

    @Test
    fun `create trims the name, normalizes the body and rejects blank names`() = runTest {
        val create = CreateProjectUseCase(repository)

        val id = (create(NewProject(name = "  Mi video  ", scriptBody = "hola\r\n")) as AppResult.Success).data
        assertThat(repository.project(id)?.name).isEqualTo("Mi video")
        assertThat(repository.script(repository.project(id)!!.scriptId)?.body).isEqualTo("hola")

        assertThat(create(NewProject(name = "   "))).isEqualTo(AppResult.Failure(AppError.InvalidInput(FIELD_NAME)))
    }

    @Test
    fun `observe filters by accent-insensitive name`() = runTest {
        repository.seed("Canción de cuna")
        repository.seed("Tutorial")
        val observe = ObserveProjectsUseCase(repository)

        assertThat(observe("CANCION").first().map { it.name }).containsExactly("Canción de cuna")
        assertThat(observe("").first()).hasSize(2)
    }

    @Test
    fun `recent projects are newest first and limited`() = runTest {
        repository.seed("Viejo", updatedAt = 1)
        repository.seed("Nuevo", updatedAt = 3)
        repository.seed("Medio", updatedAt = 2)

        val recent = ObserveRecentProjectsUseCase(repository)(limit = 2).first()
        assertThat(recent.map { it.name }).containsExactly("Nuevo", "Medio").inOrder()
    }

    @Test
    fun `duplicate picks the next free copy name`() = runTest {
        val original = repository.seed("Demo", body = "texto")
        repository.seed("Demo (2)")

        val copyId = (DuplicateProjectUseCase(repository)(original.id) as AppResult.Success).data

        val copy = repository.project(copyId)!!
        assertThat(copy.name).isEqualTo("Demo (3)")
        assertThat(repository.script(copy.scriptId)?.body).isEqualTo("texto")
    }

    @Test
    fun `duplicate of a missing project fails with not found`() = runTest {
        assertThat(DuplicateProjectUseCase(repository)("nope")).isInstanceOf(AppResult.Failure::class.java)
    }
}

class ImportScriptUseCaseTest {

    private val useCase = ImportScriptUseCase(
        FakeTextDocumentReader(mapOf("content://ok" to "﻿Hola\r\nmundo\n", "content://blank" to " \n ")),
    )

    @Test
    fun `document text is normalized`() = runTest {
        assertThat(useCase.fromDocument("content://ok")).isEqualTo(AppResult.Success("Hola\nmundo"))
    }

    @Test
    fun `blank document or clipboard is reported as empty`() = runTest {
        assertThat(useCase.fromDocument("content://blank")).isEqualTo(AppResult.Failure(AppError.EmptyContent))
        assertThat(useCase.fromClipboard(null)).isEqualTo(AppResult.Failure(AppError.EmptyContent))
    }

    @Test
    fun `reader failures pass through`() = runTest {
        assertThat((useCase.fromDocument("content://missing") as AppResult.Failure).error)
            .isInstanceOf(AppError.FileNotReadable::class.java)
    }

    @Test
    fun `oversized clipboard text is rejected`() {
        val huge = "a".repeat(com.arnoldcode.glassprompt.domain.script.ScriptText.MAX_IMPORT_CHARS + 1)
        assertThat(useCase.fromClipboard(huge)).isEqualTo(AppResult.Failure(AppError.FileTooLarge))
    }
}
