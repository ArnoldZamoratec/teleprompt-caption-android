package com.arnoldcode.glassprompt.transcription

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arnoldcode.glassprompt.core.common.AndroidLogger
import com.arnoldcode.glassprompt.core.common.DefaultDispatcherProvider
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.data.multimedia.audio.MediaCodecAudioExtractor
import com.arnoldcode.glassprompt.data.multimedia.transcription.AndroidSpeechEngine
import com.arnoldcode.glassprompt.data.storage.MediaFileStore
import com.arnoldcode.glassprompt.domain.transcription.EnergyVad
import com.arnoldcode.glassprompt.domain.transcription.TranscriptionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual diagnostic, not part of the suite: runs extraction, VAD and the device recognizer on the
 * newest real take in app storage and logs every step under the "GP/" tags. Skipped when the
 * device has no takes. Run it with `adb shell am instrument` (which keeps app data), e.g.
 * `-e class com.arnoldcode.glassprompt.transcription.SpeechEngineDiagnosticTest -e language es-ES`.
 */
@RunWith(AndroidJUnit4::class)
class SpeechEngineDiagnosticTest {

    @Test
    fun recognizeNewestTake(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val language = InstrumentationRegistry.getArguments().getString("language") ?: "es-ES"
        val logger = AndroidLogger()
        val files = MediaFileStore(context, IdGenerator.Uuid, logger)
        val take = files.takesDir.listFiles { f -> f.extension == "mp4" }?.maxByOrNull { it.lastModified() }
        assumeTrue("No takes on this device", take != null)

        val extractor = MediaCodecAudioExtractor(files, IdGenerator.Uuid, DefaultDispatcherProvider, logger)
        val audio = checkNotNull(extractor.extract(take!!.absolutePath) {})
        try {
            val regions = EnergyVad.splitLong(EnergyVad.detect(audio.frameDbs, audio.durationMs), audio.frameDbs, 20_000)
            Log.d(TAG, "Take ${take.name}: ${audio.durationMs} ms, ${regions.size} regions: $regions")
            val engine = AndroidSpeechEngine(context, DefaultDispatcherProvider, logger)
            Log.d(TAG, "Model status for $language: ${engine.status(language)}")
            val request = TranscriptionRequest(audio.pcmPath, audio.sampleRate, audio.durationMs, regions, "", language)
            val transcript = runCatching { engine.transcribe(request) {} }
            Log.d(TAG, "Transcript: ${transcript.getOrNull()?.words?.joinToString(" ") { it.text }} error=${transcript.exceptionOrNull()}")
        } finally {
            extractor.discard(audio)
        }
    }

    private companion object {
        const val TAG = "GP/Diagnostic"
    }
}
