package com.arnoldcode.glassprompt.data.multimedia.transcription

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.domain.model.CaptionWord
import com.arnoldcode.glassprompt.domain.model.Transcript
import com.arnoldcode.glassprompt.domain.transcription.ScriptAlignmentEngine
import com.arnoldcode.glassprompt.domain.transcription.SpeechModelStatus
import com.arnoldcode.glassprompt.domain.transcription.SpeechModels
import com.arnoldcode.glassprompt.domain.transcription.SpeechRegion
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionEngine
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.RandomAccessFile
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The device's own offline speech recognizer (usually Google's), fed the recorded audio instead
 * of the microphone. Needs Android 13+ ([RecognizerIntent.EXTRA_AUDIO_SOURCE]); word timings
 * come from the recognizer on Android 14+ and are estimated per speech region otherwise.
 *
 * Every speech region is its own recognition session: recognizers end a session at the first
 * long pause, and the region's start gives the words their place on the timeline.
 */
class AndroidSpeechEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : TranscriptionEngine, SpeechModels {

    override val id: String = ID

    override suspend fun isAvailable(language: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && resolveLanguage(language) != null

    override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Float) -> Unit): Transcript {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw UnsupportedOperationException("Needs Android 13")
        val language = resolveLanguage(request.language) ?: error("Language ${request.language} not installed")
        val words = capitalizeSentences(Api33.transcribe(context, dispatchers, request, language, onProgress) { logger.d(TAG, it) })
        if (words.isEmpty()) error("Recognizer returned no words")
        return Transcript(
            words = words,
            language = language,
            engineId = id,
            hasWordTimings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )
    }

    override suspend fun status(language: String): SpeechModelStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return SpeechModelStatus.UNAVAILABLE
        val support = runCatching { Api33.support(context, dispatchers, language) }.getOrNull()
            ?: return SpeechModelStatus.UNAVAILABLE
        return when {
            bestMatch(language, support.installedOnDeviceLanguages) != null -> SpeechModelStatus.INSTALLED
            bestMatch(language, support.pendingOnDeviceLanguages) != null -> SpeechModelStatus.DOWNLOADING
            bestMatch(language, support.supportedOnDeviceLanguages) != null -> SpeechModelStatus.DOWNLOADABLE
            else -> SpeechModelStatus.UNSUPPORTED
        }
    }

    override suspend fun requestDownload(language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val support = runCatching { Api33.support(context, dispatchers, language) }.getOrNull() ?: return
        val target = bestMatch(language, support.supportedOnDeviceLanguages) ?: return
        runCatching { Api33.triggerDownload(context, dispatchers, target) }
            .onFailure { logger.w(TAG, "Model download request failed", it) }
    }

    /** The installed on-device language matching [language] (exact tag first, then same language), or null. */
    private suspend fun resolveLanguage(language: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val support = runCatching { Api33.support(context, dispatchers, language) }
            .onFailure { logger.w(TAG, "Recognition support check failed", it) }
            .getOrNull()
        if (support == null) {
            logger.d(TAG, "On-device recognition unavailable")
            return null
        }
        // Only installed models count. Downloading one shows a system dialog, which a background
        // transcription must never do; without the model, captions come from the script instead.
        val match = bestMatch(language, support.installedOnDeviceLanguages)
        logger.d(
            TAG,
            "Requested $language → $match (installed=${support.installedOnDeviceLanguages}, " +
                "supported=${support.supportedOnDeviceLanguages}, pending=${support.pendingOnDeviceLanguages})",
        )
        return match
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {

        suspend fun support(context: Context, dispatchers: DispatcherProvider, language: String): RecognitionSupport? =
            withContext(dispatchers.main) {
                if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return@withContext null
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                try {
                    withTimeoutOrNull(SUPPORT_TIMEOUT_MS) {
                        suspendCancellableCoroutine { cont ->
                            recognizer.checkRecognitionSupport(
                                recognizeIntent(language),
                                context.mainExecutor,
                                object : RecognitionSupportCallback {
                                    override fun onSupportResult(support: RecognitionSupport) {
                                        if (cont.isActive) cont.resume(support)
                                    }

                                    override fun onError(error: Int) {
                                        if (cont.isActive) cont.resume(null)
                                    }
                                },
                            )
                        }
                    }
                } finally {
                    recognizer.destroy()
                }
            }

        suspend fun triggerDownload(context: Context, dispatchers: DispatcherProvider, language: String) =
            withContext(dispatchers.main) {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                try {
                    recognizer.triggerModelDownload(recognizeIntent(language))
                } finally {
                    recognizer.destroy()
                }
            }

        suspend fun transcribe(
            context: Context,
            dispatchers: DispatcherProvider,
            request: TranscriptionRequest,
            language: String,
            onProgress: (Float) -> Unit,
            log: (String) -> Unit,
        ): List<CaptionWord> = withContext(dispatchers.main) {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                val words = ArrayList<CaptionWord>()
                val regions = request.regions.ifEmpty { listOf(SpeechRegion(0, request.durationMs)) }
                regions.forEachIndexed { index, region ->
                    words += recognizeRegion(recognizer, dispatchers, request, region, language, log)
                    onProgress((index + 1f) / regions.size)
                }
                words
            } finally {
                recognizer.destroy()
            }
        }

        private suspend fun recognizeRegion(
            recognizer: SpeechRecognizer,
            dispatchers: DispatcherProvider,
            request: TranscriptionRequest,
            region: SpeechRegion,
            language: String,
            log: (String) -> Unit,
        ): List<CaptionWord> = coroutineScope {
            val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
            // Stream the region's samples into the pipe; closing it tells the recognizer the audio ended.
            // A recognizer that stops reading early breaks the pipe: that's not an error for us.
            val writer = launch(dispatchers.io) {
                runCatching { writeRegion(writeSide, request, region) }
            }
            try {
                val intent = recognizeIntent(language).apply {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, request.sampleRate)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
                    }
                }
                val results = withTimeout(region.durationMs + REGION_TIMEOUT_MARGIN_MS) { listen(recognizer, intent, log) }
                val words = results?.let { wordsOf(it, region) }.orEmpty()
                log("Region ${region.startMs}-${region.endMs} ms: ${words.size} words")
                words
            } finally {
                writer.cancel()
                readSide.close()
            }
        }

        private fun writeRegion(writeSide: ParcelFileDescriptor, request: TranscriptionRequest, region: SpeechRegion) {
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                RandomAccessFile(request.pcmPath, "r").use { file ->
                    val bytesPerMs = request.sampleRate * 2L / 1000
                    val from = (region.startMs * bytesPerMs).coerceAtMost(file.length()) and 1L.inv()
                    val to = (region.endMs * bytesPerMs).coerceAtMost(file.length())
                    file.seek(from)
                    val buffer = ByteArray(16 * 1024)
                    var left = to - from
                    while (left > 0) {
                        val n = file.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        left -= n
                    }
                    // Trailing silence lets the endpointer close the utterance cleanly.
                    out.write(ByteArray((bytesPerMs * TRAILING_SILENCE_MS).toInt()))
                }
            }
        }

        /**
         * Results bundle, or null when the region held no recognizable speech. Some recognizers
         * (Google's on-device one included) deliver the final text in a partial result flagged
         * `final_result` and call onResults with an empty bundle, so the latest partial with text
         * is kept as the answer.
         */
        private suspend fun listen(recognizer: SpeechRecognizer, intent: Intent, log: (String) -> Unit): Bundle? =
            suspendCancellableCoroutine { cont ->
                var latest: Bundle? = null
                var latestIsFinal = false
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        if (cont.isActive) cont.resume(results.takeIf { it.hasText() } ?: latest)
                    }

                    override fun onError(error: Int) {
                        if (!cont.isActive) return
                        when {
                            latest != null -> cont.resume(latest)
                            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume(null)
                            else -> {
                                log("Recognizer error $error")
                                cont.resumeWithException(IllegalStateException("SpeechRecognizer error $error"))
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.takeIf { it.hasText() } ?: return
                        val isFinal = partial.getBoolean(KEY_FINAL_RESULT)
                        // Never let a later, non-final partial replace the final text.
                        if (isFinal || !latestIsFinal) {
                            latest = partial
                            latestIsFinal = isFinal
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                cont.invokeOnCancellation { recognizer.cancel() }
                recognizer.startListening(intent)
            }

        private fun Bundle.hasText(): Boolean =
            getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.isNotBlank() == true

        private fun wordsOf(results: Bundle, region: SpeechRegion): List<CaptionWord> {
            val timed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Api34.timedWords(results, region) else emptyList()
            val words = timed.ifEmpty {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                // No per-word times: spread the region's words by length, like the script alignment does.
                ScriptAlignmentEngine.align(text, listOf(region))
            }
            return words
        }

        private fun recognizeIntent(language: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Needed: some recognizers only deliver the final text as a partial result.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        fun timedWords(results: Bundle, region: SpeechRegion): List<CaptionWord> {
            val parts = results.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
                ?.filter { (it.formattedText ?: it.rawText).isNotBlank() }
                .orEmpty()
            if (parts.isEmpty() || parts.all { it.timestampMillis == 0L }) return emptyList()
            return parts.mapIndexed { i, part ->
                val start = (region.startMs + part.timestampMillis).coerceIn(region.startMs, region.endMs)
                val nextStart = parts.getOrNull(i + 1)?.let { region.startMs + it.timestampMillis } ?: region.endMs
                CaptionWord(
                    text = part.formattedText ?: part.rawText,
                    startMs = start,
                    endMs = nextStart.coerceIn(start, region.endMs),
                )
            }
        }
    }

    companion object {
        const val ID = "android-on-device"
        private const val TAG = "SpeechEngine"
        private const val SUPPORT_TIMEOUT_MS = 3_000L
        private const val REGION_TIMEOUT_MARGIN_MS = 15_000L
        private const val TRAILING_SILENCE_MS = 600L

        /** Undocumented but stable key Google's recognizer sets on the partial that holds the final text. */
        private const val KEY_FINAL_RESULT = "final_result"

        /** A pause this long usually ends a sentence. */
        private const val SENTENCE_PAUSE_MS = 1_000L

        /**
         * Recognizers return lowercase, unpunctuated text. Capitalizes the first word and every
         * word after a long pause; short pauses (breaths mid-sentence) keep lowercase.
         */
        internal fun capitalizeSentences(words: List<CaptionWord>): List<CaptionWord> =
            words.mapIndexed { i, word ->
                val startsSentence = i == 0 || word.startMs - words[i - 1].endMs >= SENTENCE_PAUSE_MS
                if (startsSentence) word.copy(text = word.text.replaceFirstChar { it.titlecase(Locale.getDefault()) }) else word
            }

        /** Exact tag, else the first tag with the same language ("es-PE" → "es-ES"). */
        internal fun bestMatch(requested: String, available: List<String>): String? {
            val wanted = Locale.forLanguageTag(requested)
            return available.firstOrNull { it.equals(requested, ignoreCase = true) }
                ?: available.firstOrNull { Locale.forLanguageTag(it).language == wanted.language }
        }
    }
}
