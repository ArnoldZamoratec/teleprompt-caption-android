package com.arnoldcode.glassprompt.feature.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.domain.usecase.ComputeScriptStatsUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.ImportScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveScriptUseCase
import com.arnoldcode.glassprompt.testing.FakeProjectRepository
import com.arnoldcode.glassprompt.testing.FakeTextDocumentReader
import com.arnoldcode.glassprompt.testing.MainDispatcherRule
import com.arnoldcode.glassprompt.testing.TestDispatcherProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptEditorViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val dispatcher get() = mainDispatcherRule.dispatcher
    private val repository = FakeProjectRepository()

    private fun viewModel(projectId: String, documents: Map<String, String> = emptyMap()) = ScriptEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ScriptEditorViewModel.ARG_PROJECT_ID to projectId)),
        getProject = GetProjectUseCase(repository),
        getScript = GetScriptUseCase(repository),
        saveScript = SaveScriptUseCase(repository),
        importScript = ImportScriptUseCase(FakeTextDocumentReader(documents)),
        computeStats = ComputeScriptStatsUseCase(),
        dispatchers = TestDispatcherProvider(dispatcher),
        appScope = CoroutineScope(dispatcher),
    )

    @Test
    fun `loads project name, text and stats, and pushes the text to the field`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "uno dos tres")
        val vm = viewModel(project.id)

        vm.commands.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(EditorCommand.SetText("uno dos tres"))
        }
        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.projectName).isEqualTo("Demo")
        assertThat(state.stats.words).isEqualTo(3)
        assertThat(state.saveStatus).isEqualTo(SaveStatus.Saved)
    }

    @Test
    fun `missing project shows not found`() = runTest(dispatcher) {
        val vm = viewModel("missing")
        advanceUntilIdle()
        assertThat(vm.uiState.value.notFound).isTrue()
    }

    @Test
    fun `autosave waits for typing to pause and saves only the latest text`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "")
        val vm = viewModel(project.id)
        advanceUntilIdle()

        vm.onTextChange("H")
        advanceTimeBy(300)
        vm.onTextChange("Hola")
        advanceTimeBy(300)
        vm.onTextChange("Hola mundo")
        assertThat(vm.uiState.value.saveStatus).isEqualTo(SaveStatus.Pending)
        assertThat(repository.savedBodies).isEmpty()

        advanceTimeBy(ScriptEditorViewModel.AUTOSAVE_DEBOUNCE_MS + 1)
        runCurrent()

        assertThat(repository.savedBodies).containsExactly("Hola mundo")
        assertThat(vm.uiState.value.saveStatus).isEqualTo(SaveStatus.Saved)
        assertThat(vm.uiState.value.stats.words).isEqualTo(2)
    }

    @Test
    fun `flush saves pending text immediately`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "a")
        val vm = viewModel(project.id)
        advanceUntilIdle()

        vm.onTextChange("a b")
        vm.flush()
        runCurrent()

        assertThat(repository.script(project.scriptId)?.body).isEqualTo("a b")
    }

    @Test
    fun `save failure is surfaced`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "a")
        val vm = viewModel(project.id)
        advanceUntilIdle()
        repository.failWith = AppError.StorageFull

        vm.onTextChange("b")
        advanceUntilIdle()

        assertThat(vm.uiState.value.saveStatus).isEqualTo(SaveStatus.Error)
        assertThat(vm.uiState.value.message).isInstanceOf(EditorMessage.Error::class.java)
    }

    @Test
    fun `search finds matches, navigates cyclically and selects them`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "gato perro Gato")
        val vm = viewModel(project.id)
        advanceUntilIdle()

        vm.commands.test {
            skipItems(1) // initial SetText
            vm.onToggleSearch()
            vm.onQueryChange("gato")
            assertThat(vm.uiState.value.search.matches).containsExactly(0..3, 11..14).inOrder()
            assertThat(awaitItem()).isEqualTo(EditorCommand.Select(0..3))

            vm.onNextMatch()
            assertThat(awaitItem()).isEqualTo(EditorCommand.Select(11..14))
            vm.onNextMatch()
            assertThat(awaitItem()).isEqualTo(EditorCommand.Select(0..3))
            vm.onPreviousMatch()
            assertThat(awaitItem()).isEqualTo(EditorCommand.Select(11..14))
        }
    }

    @Test
    fun `replace current moves to the next match and replace all reports the count`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "gato gato gato")
        val vm = viewModel(project.id)
        advanceUntilIdle()
        vm.onToggleSearch()
        vm.onQueryChange("gato")
        vm.onReplacementChange("león")

        vm.onReplaceCurrent()
        assertThat(vm.uiState.value.text).isEqualTo("león gato gato")
        assertThat(vm.uiState.value.search.current).isEqualTo(0)
        assertThat(vm.uiState.value.search.matches).hasSize(2)

        vm.onReplaceAll()
        assertThat(vm.uiState.value.text).isEqualTo("león león león")
        assertThat(vm.uiState.value.message).isEqualTo(EditorMessage.Replaced(2))

        advanceUntilIdle()
        assertThat(repository.script(project.scriptId)?.body).isEqualTo("león león león")
    }

    @Test
    fun `import into an empty script applies directly`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "")
        val vm = viewModel(project.id, documents = mapOf("content://doc" to "Texto importado\r\n"))
        advanceUntilIdle()

        vm.onImportDocument("content://doc")
        advanceUntilIdle()

        assertThat(vm.uiState.value.text).isEqualTo("Texto importado")
        assertThat(vm.uiState.value.pendingImport).isNull()
        assertThat(vm.uiState.value.message).isEqualTo(EditorMessage.Imported)
    }

    @Test
    fun `import into existing text asks, then appends or replaces`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "Existente")
        val vm = viewModel(project.id)
        advanceUntilIdle()

        vm.onPasteFromClipboard("Nuevo")
        assertThat(vm.uiState.value.pendingImport).isEqualTo("Nuevo")
        vm.onImportAppend()
        assertThat(vm.uiState.value.text).isEqualTo("Existente\n\nNuevo")

        vm.onPasteFromClipboard("Solo esto")
        vm.onImportReplace()
        assertThat(vm.uiState.value.text).isEqualTo("Solo esto")
    }

    @Test
    fun `empty clipboard shows a friendly message`() = runTest(dispatcher) {
        val project = repository.seed("Demo", body = "x")
        val vm = viewModel(project.id)
        advanceUntilIdle()

        vm.onPasteFromClipboard("   ")

        assertThat(vm.uiState.value.message).isEqualTo(EditorMessage.ClipboardEmpty)
        assertThat(vm.uiState.value.text).isEqualTo("x")
    }
}
