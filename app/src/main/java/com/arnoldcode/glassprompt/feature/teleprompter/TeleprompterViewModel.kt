package com.arnoldcode.glassprompt.feature.teleprompter

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnoldcode.glassprompt.core.common.ApplicationScope
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.model.TextPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class TeleprompterUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val projectName: String = "",
    val text: String = "",
    val wordCount: Int = 0,
    val settings: TeleprompterSettings = TeleprompterSettings(),
)

/** Rehearsal teleprompter (no camera). */
@HiltViewModel
class TeleprompterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: PrompterSettingsSession,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle[ARG_PROJECT_ID]) { "projectId is required" }

    private val _uiState = MutableStateFlow(TeleprompterUiState())
    val uiState: StateFlow<TeleprompterUiState> = _uiState.asStateFlow()

    init {
        session.start(viewModelScope)
        viewModelScope.launch {
            val content = session.load(projectId)
            _uiState.update {
                if (content == null) {
                    it.copy(isLoading = false, notFound = true)
                } else {
                    it.copy(
                        isLoading = false,
                        projectName = content.project.name,
                        text = content.text,
                        wordCount = content.wordCount,
                        settings = content.project.teleprompter,
                    )
                }
            }
        }
    }

    fun onSpeedStep(up: Boolean) = edit { PrompterAdjust.speed(it, up) }
    fun onFontSizeChange(size: Float) = edit { PrompterAdjust.fontSize(it, size) }
    fun onFontStep(up: Boolean) = edit { PrompterAdjust.fontStep(it, up) }
    fun onMirrorToggle() = edit { it.copy(mirror = !it.mirror) }
    fun onPositionSelected(position: TextPosition) = edit { it.copy(position = position) }

    private inline fun edit(transform: (TeleprompterSettings) -> TeleprompterSettings) {
        val updated = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = updated) }
        session.onChanged(updated)
    }

    override fun onCleared() {
        appScope.launch { session.flush() }
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
