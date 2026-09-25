package com.arnoldcode.glassprompt.feature.teleprompter

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.Project
import com.arnoldcode.glassprompt.domain.model.TeleprompterSettings
import com.arnoldcode.glassprompt.domain.script.ScriptText
import com.arnoldcode.glassprompt.domain.teleprompter.ScrollEngine
import com.arnoldcode.glassprompt.domain.usecase.GetProjectUseCase
import com.arnoldcode.glassprompt.domain.usecase.GetScriptUseCase
import com.arnoldcode.glassprompt.domain.usecase.UpdateTeleprompterSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A project's script ready for the prompter. */
data class PrompterContent(
    val project: Project,
    val text: String,
    val wordCount: Int,
)

/**
 * Loads a project's script for reading and persists teleprompter tweaks (speed, size, mirror…)
 * [SAVE_DEBOUNCE_MS] after the last change, so a pinch gesture does not write on every frame.
 * Shared by the rehearsal teleprompter and the camera screen.
 */
@OptIn(FlowPreview::class)
class PrompterSettingsSession @Inject constructor(
    private val getProject: GetProjectUseCase,
    private val getScript: GetScriptUseCase,
    private val updateSettings: UpdateTeleprompterSettingsUseCase,
) {
    private var project: Project? = null
    private val pending = MutableStateFlow<TeleprompterSettings?>(null)

    suspend fun load(projectId: String): PrompterContent? {
        val loaded = (getProject(projectId) as? AppResult.Success)?.data ?: return null
        val script = (getScript(loaded.scriptId) as? AppResult.Success)?.data ?: return null
        project = loaded
        return PrompterContent(loaded, script.body, ScriptText.countWords(script.body))
    }

    /** Starts the debounced writer; call once from the owner's scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            pending.drop(1).filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    fun onChanged(settings: TeleprompterSettings) {
        pending.value = settings
    }

    /** Writes any pending change now (screen leaving). */
    suspend fun flush() {
        pending.value?.let { save(it) }
    }

    private suspend fun save(settings: TeleprompterSettings) {
        val current = project ?: return
        if (updateSettings(current, settings) is AppResult.Success) project = current.copy(teleprompter = settings)
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}

/** Setting edits shared by both prompter screens, with the same limits everywhere. */
object PrompterAdjust {
    fun speed(settings: TeleprompterSettings, up: Boolean) = settings.copy(speed = ScrollEngine.step(settings.speed, up))

    fun fontSize(settings: TeleprompterSettings, size: Float) =
        settings.copy(fontSizeSp = size.coerceIn(TeleprompterSettings.FontSizeRange))

    fun fontStep(settings: TeleprompterSettings, up: Boolean) = fontSize(settings, settings.fontSizeSp + if (up) FONT_STEP else -FONT_STEP)

    private const val FONT_STEP = 4f
}
