package com.arnoldcode.glassprompt.feature.captions

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.R
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.ApplicationScope
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.domain.captions.CaptionEdits
import com.arnoldcode.glassprompt.domain.captions.CaptionSegmenter
import com.arnoldcode.glassprompt.domain.model.Caption
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionStyles
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.usecase.ObserveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.ObserveTakeUseCase
import com.arnoldcode.glassprompt.domain.usecase.SaveCaptionTrackUseCase
import com.arnoldcode.glassprompt.domain.usecase.TranscriptionControlUseCase
import com.arnoldcode.glassprompt.feature.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

@Immutable
data class CaptionEditorUiState(
    val isLoading: Boolean = true,
    val take: Take? = null,
    val captions: List<Caption> = emptyList(),
    val style: CaptionStyle = CaptionStyle(),
    /** False until the take has been transcribed at least once. */
    val hasTrack: Boolean = false,
    /** Which engine produced the captions (shown so users know if their voice or the script was used). */
    val engineId: String = "",
    val transcription: TranscriptionState = TranscriptionState.Idle,
    val selectedId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val confirmRetranscribe: Boolean = false,
    @param:StringRes val message: Int? = null,
) {
    val selected: Caption? get() = captions.firstOrNull { it.id == selectedId }
    val isTranscribing: Boolean get() = transcription is TranscriptionState.Running
}

/**
 * Caption editor. All edits go through the pure [CaptionEdits] operations, are undoable, and are
 * saved [AUTOSAVE_DEBOUNCE_MS] after the last change (and when the screen goes away). A new
 * transcription replaces the local state and clears the history.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CaptionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTake: ObserveTakeUseCase,
    private val observeTrack: ObserveCaptionTrackUseCase,
    private val saveTrack: SaveCaptionTrackUseCase,
    private val transcription: TranscriptionControlUseCase,
    private val ids: IdGenerator,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val takeId: String = checkNotNull(savedStateHandle[ARG_TAKE_ID]) { "takeId is required" }

    private val _uiState = MutableStateFlow(CaptionEditorUiState())
    val uiState: StateFlow<CaptionEditorUiState> = _uiState.asStateFlow()

    private data class Snapshot(val captions: List<Caption>, val style: CaptionStyle)

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    /** Consecutive typing in one caption is one undo step. */
    private var lastTypedId: String? = null

    /** Track metadata kept from the repository (language, engine). */
    private var baseTrack: CaptionTrack? = null
    /** What we last wrote, to tell our own saves apart from a new transcription. */
    private var lastSaved: Snapshot? = null
    private val saveMutex = Mutex()
    private val edits = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val take = observeTake(takeId).first()?.takeIf { File(it.filePath).exists() }
            if (take == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(take = take) }
            if (observeTrack(takeId).first() == null) startIfNeverTranscribed()
            observeTrack(takeId).collect(::onTrack)
        }
        viewModelScope.launch {
            transcription.observe(takeId).collect { state -> _uiState.update { it.copy(transcription = state) } }
        }
        viewModelScope.launch {
            edits.debounce(AUTOSAVE_DEBOUNCE_MS).collect { if (it > 0) save() }
        }
    }

    /** Opening a take without captions starts its transcription, unless one is running or just failed. */
    private suspend fun startIfNeverTranscribed() {
        when (transcription.observe(takeId).first()) {
            TranscriptionState.Idle -> transcription.start(takeId)
            // Finished but no track: it was deleted since, so transcribe again.
            TranscriptionState.Done -> transcription.start(takeId, replace = true)
            else -> Unit
        }
    }

    private fun onTrack(track: CaptionTrack?) {
        if (track == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        baseTrack = track
        val incoming = Snapshot(track.captions.sortedBy { it.startMs }, track.style)
        // Our own save echoing back: keep the local state (and the user's selection/history).
        if (incoming == lastSaved) return
        undo.clear()
        redo.clear()
        lastSaved = incoming
        _uiState.update {
            it.copy(
                isLoading = false,
                hasTrack = true,
                engineId = track.engineId,
                captions = incoming.captions,
                style = incoming.style,
                selectedId = it.selectedId?.takeIf { id -> incoming.captions.any { c -> c.id == id } },
                canUndo = false,
                canRedo = false,
            )
        }
    }

    // region Selection

    fun onSelect(id: String?) = _uiState.update { it.copy(selectedId = id) }

    // endregion

    // region Caption edits

    fun onTextChange(id: String, text: String) {
        apply(coalesce = lastTypedId == id) { CaptionEdits.editText(it, id, text) }
        lastTypedId = id
    }

    /** Splits at the playhead: words that start before it stay in the first half. */
    fun onSplit(id: String, playheadMs: Long) = apply { captions ->
        val caption = captions.firstOrNull { it.id == id } ?: return@apply captions
        val wordIndex = caption.words.indexOfFirst { it.startMs >= playheadMs }.takeIf { it > 0 }
        CaptionEdits.split(captions, id, wordIndex, ids.newId())
    }

    fun onMergeWithNext(id: String) = apply { CaptionEdits.mergeWithNext(it, id) }

    fun onDelete(id: String) {
        apply { CaptionEdits.delete(it, id) }
        _uiState.update { it.copy(selectedId = null) }
    }

    fun onInsertAt(playheadMs: Long, text: String) {
        val newId = ids.newId()
        val before = _uiState.value.captions
        apply { CaptionEdits.insert(it, playheadMs, text, newId, videoDurationMs()) }
        if (_uiState.value.captions.size > before.size) {
            _uiState.update { it.copy(selectedId = newId) }
        } else {
            _uiState.update { it.copy(message = R.string.captions_no_room) }
        }
    }

    fun onNudge(id: String, startDeltaMs: Long, endDeltaMs: Long) = apply { captions ->
        val caption = captions.firstOrNull { it.id == id } ?: return@apply captions
        CaptionEdits.retime(captions, id, caption.startMs + startDeltaMs, caption.endMs + endDeltaMs, videoDurationMs())
    }

    fun onSetStart(id: String, atMs: Long) = apply { captions ->
        val caption = captions.firstOrNull { it.id == id } ?: return@apply captions
        CaptionEdits.retime(captions, id, atMs, caption.endMs, videoDurationMs())
    }

    fun onSetEnd(id: String, atMs: Long) = apply { captions ->
        val caption = captions.firstOrNull { it.id == id } ?: return@apply captions
        CaptionEdits.retime(captions, id, caption.startMs, atMs, videoDurationMs())
    }

    // endregion

    // region Style

    fun onPreset(preset: CaptionPreset) {
        val current = _uiState.value.style
        // Presets keep the words-per-line grouping, so picking a look never reflows the captions.
        applyStyle(CaptionStyles.of(preset).copy(maxWordsPerLine = current.maxWordsPerLine))
    }

    fun onStyleChange(change: (CaptionStyle) -> CaptionStyle) {
        val current = _uiState.value.style
        val changed = change(current)
        if (changed == current) return
        applyStyle(changed.copy(preset = CaptionPreset.CUSTOM))
    }

    /** Regroups every word into captions of the new width. Undoable. */
    fun onWordsPerLine(words: Int) {
        val state = _uiState.value
        val value = words.coerceIn(CaptionStyle.WordsPerLineRange)
        if (value == state.style.maxWordsPerLine) return
        pushHistory(coalesce = false)
        val allWords = state.captions.flatMap { caption ->
            val tokens = caption.text.split(Whitespace).count { it.isNotBlank() }
            // Edited captions may have lost their timings: estimate them from the caption's span.
            if (caption.words.size == tokens) caption.words else CaptionEdits.spreadWords(caption.text, caption.startMs, caption.endMs)
        }
        val regrouped = CaptionSegmenter.segment(allWords, value) { ids.newId() }
        commit(regrouped, state.style.copy(maxWordsPerLine = value, preset = CaptionPreset.CUSTOM))
        _uiState.update { it.copy(selectedId = null) }
    }

    private fun applyStyle(style: CaptionStyle) {
        pushHistory(coalesce = false)
        commit(_uiState.value.captions, style)
    }

    // endregion

    // region Undo / redo

    fun onUndo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(current())
        restore(previous)
    }

    fun onRedo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(current())
        restore(next)
    }

    private fun restore(snapshot: Snapshot) {
        lastTypedId = null
        commit(snapshot.captions, snapshot.style)
    }

    // endregion

    // region Transcription

    fun onRetranscribeRequest() = _uiState.update { it.copy(confirmRetranscribe = true) }

    fun onRetranscribeDismiss() = _uiState.update { it.copy(confirmRetranscribe = false) }

    /** Replaces the captions with a fresh transcription (the style is kept). */
    fun onRetranscribe(mode: TranscriptionMode?) {
        _uiState.update { it.copy(confirmRetranscribe = false) }
        viewModelScope.launch {
            flushNow()
            transcription.start(takeId, mode, replace = true)
        }
    }

    fun onCancelTranscription() = transcription.cancel(takeId)

    // endregion

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** Saves pending edits immediately (screen stop). */
    fun flush() {
        if (edits.value > 0 && current() != lastSaved) appScope.launch { save() }
    }

    override fun onCleared() {
        flush()
    }

    private fun apply(coalesce: Boolean = false, edit: (List<Caption>) -> List<Caption>) {
        val state = _uiState.value
        if (!state.hasTrack) return
        val edited = edit(state.captions)
        if (edited == state.captions) return
        if (!coalesce) lastTypedId = null
        pushHistory(coalesce)
        commit(edited, state.style)
    }

    private fun pushHistory(coalesce: Boolean) {
        if (!coalesce) {
            undo.addLast(current())
            if (undo.size > MAX_HISTORY) undo.removeFirst()
        }
        redo.clear()
    }

    private fun commit(captions: List<Caption>, style: CaptionStyle) {
        _uiState.update {
            it.copy(
                captions = captions,
                style = style,
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
                selectedId = it.selectedId?.takeIf { id -> captions.any { c -> c.id == id } },
            )
        }
        edits.update { it + 1 }
    }

    private fun current() = Snapshot(_uiState.value.captions, _uiState.value.style)

    private fun videoDurationMs(): Long = _uiState.value.take?.durationMs ?: 0L

    private suspend fun flushNow() {
        if (edits.value > 0 && current() != lastSaved) save()
    }

    /** Always writes the latest state, so overlapping autosave/flush calls never persist stale captions. */
    private suspend fun save() = saveMutex.withLock {
        val base = baseTrack ?: return@withLock
        val snapshot = current()
        if (snapshot == lastSaved) return@withLock
        lastSaved = snapshot
        val result = saveTrack(base.copy(captions = snapshot.captions, style = snapshot.style))
        if (result is AppResult.Failure) {
            lastSaved = null
            _uiState.update { it.copy(message = result.error.messageRes()) }
        }
    }

    companion object {
        const val ARG_TAKE_ID = "takeId"
        const val AUTOSAVE_DEBOUNCE_MS = 600L
        private const val MAX_HISTORY = 100
        private val Whitespace = Regex("\\s+")
    }
}
