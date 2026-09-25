package com.arnoldcode.glassprompt.feature.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.ApplicationScope
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.domain.script.ScriptSearch
import com.arnoldcode.glassprompt.domain.script.ScriptStats
import com.arnoldcode.glassprompt.domain.script.ScriptText
import com.arnoldcode.glassprompt.domain.usecase.ComputeScriptStatsUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.ImportScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveScriptUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SaveStatus { Saved, Pending, Saving, Error }

@Immutable
data class SearchState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val matches: List<IntRange> = emptyList(),
    val current: Int = -1,
)

sealed interface EditorMessage {
    data class Replaced(val count: Int) : EditorMessage
    data object Imported : EditorMessage
    data object ClipboardEmpty : EditorMessage
    data class Error(@param:StringRes val res: Int) : EditorMessage
}

@Immutable
data class EditorUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val projectName: String = "",
    val text: String = "",
    val stats: ScriptStats = ScriptStats.Empty,
    val saveStatus: SaveStatus = SaveStatus.Saved,
    val search: SearchState = SearchState(),
    /** Imported text waiting for the user to choose "replace" or "append". */
    val pendingImport: String? = null,
    val message: EditorMessage? = null,
)

/** Imperative edits the text field must apply (the field owns cursor/selection state). */
sealed interface EditorCommand {
    data class SetText(val text: String, val selection: IntRange? = null) : EditorCommand
    data class Select(val range: IntRange) : EditorCommand
}

/**
 * Script editor. The text field reports every edit through [onTextChange]; the body is saved
 * [AUTOSAVE_DEBOUNCE_MS] after typing stops and flushed on [flush] (screen stop) and when the
 * ViewModel is cleared, so leaving the screen never loses text.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ScriptEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProject: GetProjectUseCase,
    private val getScript: GetScriptUseCase,
    private val saveScript: SaveScriptUseCase,
    private val importScript: ImportScriptUseCase,
    private val computeStats: ComputeScriptStatsUseCase,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle[ARG_PROJECT_ID]) { "projectId is required" }

    private var scriptId: String? = null
    private var readingSpeed = 1f
    private var lastSaved = ""
    private val saveMutex = Mutex()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _commands = Channel<EditorCommand>(Channel.BUFFERED)
    val commands: Flow<EditorCommand> = _commands.receiveAsFlow()

    /** Latest text; drives autosave and stats. */
    private val edits = MutableStateFlow("")

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            edits.drop(1).debounce(AUTOSAVE_DEBOUNCE_MS).collect { save() }
        }
        viewModelScope.launch {
            edits.drop(1).debounce(STATS_DEBOUNCE_MS).collect { text ->
                val stats = withContext(dispatchers.default) { computeStats(text, readingSpeed) }
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }

    private suspend fun load() {
        val project = (getProject(projectId) as? AppResult.Success)?.data
        val script = project?.let { (getScript(it.scriptId) as? AppResult.Success)?.data }
        if (project == null || script == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        scriptId = script.id
        readingSpeed = project.teleprompter.speed
        lastSaved = script.body
        val stats = withContext(dispatchers.default) { computeStats(script.body, readingSpeed) }
        _uiState.update {
            it.copy(isLoading = false, projectName = project.name, text = script.body, stats = stats)
        }
        _commands.send(EditorCommand.SetText(script.body))
    }

    // region Editing & saving

    fun onTextChange(text: String) {
        if (text == _uiState.value.text) return
        _uiState.update { state ->
            state.copy(text = text, saveStatus = SaveStatus.Pending, search = state.search.recomputed(text))
        }
        edits.value = text
    }

    /** Saves immediately if there are unsaved edits (called when the screen stops). */
    fun flush() {
        if (_uiState.value.text != lastSaved) appScope.launch { save() }
    }

    override fun onCleared() {
        flush()
    }

    /** Always persists the latest text, so concurrent autosave/flush calls can never write stale content. */
    private suspend fun save() = saveMutex.withLock {
        val id = scriptId ?: return@withLock
        val text = _uiState.value.text
        if (text == lastSaved) {
            _uiState.update { it.copy(saveStatus = SaveStatus.Saved) }
            return@withLock
        }
        _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }
        when (val result = saveScript(id, text)) {
            is AppResult.Success -> {
                lastSaved = text
                _uiState.update { it.copy(saveStatus = if (it.text == text) SaveStatus.Saved else SaveStatus.Pending) }
            }
            is AppResult.Failure -> _uiState.update {
                it.copy(saveStatus = SaveStatus.Error, message = EditorMessage.Error(result.error.messageRes()))
            }
        }
    }

    /** Programmatic text change (replace, import): update state and tell the field. */
    private fun applyText(text: String, selection: IntRange? = null) {
        _uiState.update { state ->
            state.copy(text = text, saveStatus = SaveStatus.Pending, search = state.search.recomputed(text))
        }
        edits.value = text
        _commands.trySend(EditorCommand.SetText(text, selection))
    }

    // endregion

    // region Find & replace

    fun onToggleSearch() {
        _uiState.update { state ->
            val visible = !state.search.visible
            state.copy(search = if (visible) state.search.copy(visible = true).recomputed(state.text) else SearchState())
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(search = state.search.copy(query = query, current = -1).recomputed(state.text))
        }
        selectCurrent()
    }

    fun onReplacementChange(replacement: String) {
        _uiState.update { it.copy(search = it.search.copy(replacement = replacement)) }
    }

    fun onNextMatch() = moveMatch(+1)

    fun onPreviousMatch() = moveMatch(-1)

    private fun moveMatch(delta: Int) {
        _uiState.update { state ->
            val count = state.search.matches.size
            if (count == 0) return@update state
            state.copy(search = state.search.copy(current = Math.floorMod(state.search.current + delta, count)))
        }
        selectCurrent()
    }

    private fun selectCurrent() {
        val search = _uiState.value.search
        search.matches.getOrNull(search.current)?.let { _commands.trySend(EditorCommand.Select(it)) }
    }

    fun onReplaceCurrent() {
        val state = _uiState.value
        val match = state.search.matches.getOrNull(state.search.current) ?: return
        val replacement = state.search.replacement
        val text = ScriptSearch.replaceAt(state.text, match, replacement)
        val caret = match.first + replacement.length
        applyText(text, caret until caret)
        // Continue with the first match after the replaced one, wrapping to the top.
        _uiState.update { s ->
            val matches = s.search.matches
            val next = matches.indexOfFirst { it.first >= caret }
            val current = when {
                matches.isEmpty() -> -1
                next >= 0 -> next
                else -> 0
            }
            s.copy(search = s.search.copy(current = current))
        }
        selectCurrent()
    }

    fun onReplaceAll() {
        val state = _uiState.value
        val (text, count) = ScriptSearch.replaceAll(state.text, state.search.query, state.search.replacement)
        if (count > 0) applyText(text)
        _uiState.update { it.copy(message = EditorMessage.Replaced(count)) }
    }

    // endregion

    // region Import

    fun onImportDocument(uri: String) {
        viewModelScope.launch {
            when (val result = importScript.fromDocument(uri)) {
                is AppResult.Success -> onImported(result.data)
                is AppResult.Failure -> _uiState.update { it.copy(message = EditorMessage.Error(result.error.messageRes())) }
            }
        }
    }

    fun onPasteFromClipboard(text: String?) {
        when (val result = importScript.fromClipboard(text)) {
            is AppResult.Success -> onImported(result.data)
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    message = if (result.error == AppError.EmptyContent) {
                        EditorMessage.ClipboardEmpty
                    } else {
                        EditorMessage.Error(result.error.messageRes())
                    },
                )
            }
        }
    }

    private fun onImported(text: String) {
        if (_uiState.value.text.isBlank()) {
            applyText(text)
            _uiState.update { it.copy(message = EditorMessage.Imported) }
        } else {
            _uiState.update { it.copy(pendingImport = text) }
        }
    }

    fun onImportReplace() = resolveImport { _, imported -> imported }

    fun onImportAppend() = resolveImport { current, imported -> ScriptText.append(current, imported) }

    fun onImportDismiss() = _uiState.update { it.copy(pendingImport = null) }

    private inline fun resolveImport(merge: (current: String, imported: String) -> String) {
        val state = _uiState.value
        val imported = state.pendingImport ?: return
        _uiState.update { it.copy(pendingImport = null, message = EditorMessage.Imported) }
        applyText(merge(state.text, imported))
    }

    // endregion

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
        const val AUTOSAVE_DEBOUNCE_MS = 600L
        const val STATS_DEBOUNCE_MS = 150L
    }
}

/** Matches for the current query on [text], keeping the current index in range. */
private fun SearchState.recomputed(text: String): SearchState {
    if (!visible) return this
    val matches = ScriptSearch.findAll(text, query)
    val current = when {
        matches.isEmpty() -> -1
        current !in matches.indices -> 0
        else -> current
    }
    return copy(matches = matches, current = current)
}
