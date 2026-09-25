package com.arnoldcode.glassprompt.domain.usecase

import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.core.common.getOrNull
import com.arnoldcode.glassprompt.domain.captions.CaptionSegmenter
import com.arnoldcode.glassprompt.domain.model.CaptionPreset
import com.arnoldcode.glassprompt.domain.model.CaptionStyle
import com.arnoldcode.glassprompt.domain.model.CaptionStyles
import com.arnoldcode.glassprompt.domain.model.CaptionTrack
import com.arnoldcode.glassprompt.domain.model.Transcript
import com.arnoldcode.glassprompt.domain.model.TranscriptionMode
import com.arnoldcode.glassprompt.domain.model.TranscriptionState
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Reason
import com.arnoldcode.glassprompt.domain.model.TranscriptionState.Stage
import com.arnoldcode.glassprompt.domain.repository.CaptionRepository
import com.arnoldcode.glassprompt.domain.repository.ProjectRepository
import com.arnoldcode.glassprompt.domain.repository.ScriptRepository
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import com.arnoldcode.glassprompt.domain.repository.TranscriptionScheduler
import com.arnoldcode.glassprompt.domain.repository.UserPreferencesRepository
import com.arnoldcode.glassprompt.domain.transcription.AudioExtractor
import com.arnoldcode.glassprompt.domain.transcription.EnergyVad
import com.arnoldcode.glassprompt.domain.transcription.ScriptAlignmentEngine
import com.arnoldcode.glassprompt.domain.transcription.SpeechRegion
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionEngine
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Take → captions: decode the audio, find the speech, recognize it (falling back engine by
 * engine, down to aligning the script, which always works), group words into captions and
 * save them. Keeps the style of an existing track, so re-transcribing never loses a restyle.
 */
class TranscribeTakeUseCase @Inject constructor(
    private val takes: TakeRepository,
    private val projects: ProjectRepository,
    private val scripts: ScriptRepository,
    private val captions: CaptionRepository,
    private val preferences: UserPreferencesRepository,
    private val audio: AudioExtractor,
    /** In order of preference; must end with the [ScriptAlignmentEngine]. */
    private val engines: List<@JvmSuppressWildcards TranscriptionEngine>,
    private val ids: IdGenerator,
    private val logger: Logger,
) {

    suspend operator fun invoke(
        takeId: String,
        mode: TranscriptionMode,
        onProgress: (Stage, Float) -> Unit = { _, _ -> },
    ): TranscriptionState {
        val take = takes.getTake(takeId).getOrNull() ?: return TranscriptionState.Failed(Reason.NOT_FOUND)
        val script = projects.getProject(take.projectId).getOrNull()
            ?.let { scripts.getScript(it.scriptId).getOrNull()?.body }
            .orEmpty()
        val prefs = preferences.preferences.first()
        val language = prefs.transcriptionLanguage.ifBlank { Locale.getDefault().toLanguageTag() }

        onProgress(Stage.EXTRACTING_AUDIO, 0f)
        val extracted = try {
            audio.extract(take.filePath) { onProgress(Stage.EXTRACTING_AUDIO, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Audio extraction failed", e)
            null
        }
        try {
            if (extracted == null && script.isBlank()) return TranscriptionState.Failed(Reason.NO_AUDIO)

            onProgress(Stage.DETECTING_SPEECH, 0f)
            val durationMs = extracted?.durationMs?.takeIf { it > 0 } ?: take.durationMs
            val regions = extracted?.let {
                EnergyVad.splitLong(EnergyVad.detect(it.frameDbs, durationMs), it.frameDbs, MAX_REGION_MS)
            }.orEmpty()
            if (regions.isEmpty() && script.isBlank()) return TranscriptionState.Failed(Reason.NO_SPEECH)

            val request = TranscriptionRequest(
                pcmPath = extracted?.pcmPath.orEmpty(),
                sampleRate = extracted?.sampleRate ?: 0,
                durationMs = durationMs,
                regions = regions.ifEmpty { listOf(SpeechRegion(0, durationMs)) },
                scriptText = script,
                language = language,
            )
            // Without decoded audio only the script can be placed on the timeline.
            val candidates = if (mode == TranscriptionMode.SCRIPT || extracted == null) {
                engines.filter { it.id == ScriptAlignmentEngine.ID }
            } else {
                engines
            }
            val transcript = recognize(candidates, request, onProgress)
                ?: return TranscriptionState.Failed(if (script.isBlank()) Reason.NO_SPEECH else Reason.ENGINE_ERROR)

            onProgress(Stage.SAVING, 0f)
            val style = captions.getTrack(takeId)?.style ?: CaptionStyles.of(prefs.defaultCaptionPreset)
            val track = CaptionTrack(
                takeId = takeId,
                captions = CaptionSegmenter.segment(transcript.words, style.maxWordsPerLine) { ids.newId() },
                style = style,
                language = transcript.language,
                engineId = transcript.engineId,
            )
            return when (captions.saveTrack(track)) {
                is AppResult.Success -> TranscriptionState.Done
                is AppResult.Failure -> TranscriptionState.Failed(Reason.ENGINE_ERROR)
            }
        } finally {
            extracted?.let(audio::discard)
        }
    }

    private suspend fun recognize(
        candidates: List<TranscriptionEngine>,
        request: TranscriptionRequest,
        onProgress: (Stage, Float) -> Unit,
    ): Transcript? {
        for (engine in candidates) {
            if (!engine.isAvailable(request.language)) continue
            onProgress(Stage.TRANSCRIBING, 0f)
            try {
                val transcript = engine.transcribe(request) { onProgress(Stage.TRANSCRIBING, it) }
                if (transcript.words.isNotEmpty()) return transcript
                logger.w(TAG, "${engine.id} returned no words, trying the next engine")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "${engine.id} failed, trying the next engine", e)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "Transcribe"

        /** Recognizers end sessions on long utterances; longer speech is split at a quiet point. */
        const val MAX_REGION_MS = 20_000L
    }
}

class ObserveCaptionTrackUseCase @Inject constructor(private val captions: CaptionRepository) {
    operator fun invoke(takeId: String): Flow<CaptionTrack?> = captions.observeTrack(takeId)
}

class SaveCaptionTrackUseCase @Inject constructor(private val captions: CaptionRepository) {
    suspend operator fun invoke(track: CaptionTrack): AppResult<Unit> = captions.saveTrack(track)
}

class SaveCaptionStyleUseCase @Inject constructor(
    private val captions: CaptionRepository,
    private val preferences: UserPreferencesRepository,
) {
    /** Saves the take's style; a built-in preset also becomes the default for future takes. */
    suspend operator fun invoke(takeId: String, style: CaptionStyle): AppResult<Unit> {
        if (style.preset != CaptionPreset.CUSTOM) {
            preferences.setDefaultCaptionPreset(style.preset)
        }
        return captions.saveStyle(takeId, style)
    }
}

/** Starts, restarts and follows background transcriptions. */
class TranscriptionControlUseCase @Inject constructor(
    private val scheduler: TranscriptionScheduler,
    private val preferences: UserPreferencesRepository,
) {
    fun observe(takeId: String): Flow<TranscriptionState> = scheduler.observe(takeId)

    /** Uses the mode chosen in settings unless [mode] overrides it. */
    suspend fun start(takeId: String, mode: TranscriptionMode? = null, replace: Boolean = false) {
        scheduler.enqueue(takeId, mode ?: preferences.preferences.first().transcriptionMode, replace)
    }

    fun cancel(takeId: String) = scheduler.cancel(takeId)
}
