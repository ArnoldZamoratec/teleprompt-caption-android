package com.arnoldcode.glassprompt.domain.transcription

import com.arnoldcode.glassprompt.domain.model.Transcript

/** A stretch of the recording where someone is speaking. */
data class SpeechRegion(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Everything an engine may use: decoded audio, where the speech is, and the script that was read. */
data class TranscriptionRequest(
    /** 16-bit little-endian mono PCM. */
    val pcmPath: String,
    val sampleRate: Int,
    val durationMs: Long,
    val regions: List<SpeechRegion>,
    val scriptText: String,
    /** BCP-47 language tag, e.g. "es-ES". */
    val language: String,
)

/** Whether the device can recognize a language offline. */
enum class SpeechModelStatus { INSTALLED, DOWNLOADING, DOWNLOADABLE, UNSUPPORTED, UNAVAILABLE }

/** The device's offline speech models. Downloads are only ever started by an explicit user action. */
interface SpeechModels {
    suspend fun status(language: String): SpeechModelStatus

    /** Opens the system's download prompt for [language]; call only from a user action. */
    suspend fun requestDownload(language: String)
}

/** Speech-to-text provider. New engines (Whisper, cloud) implement this without touching callers. */
interface TranscriptionEngine {
    val id: String

    suspend fun isAvailable(language: String): Boolean

    /** Throws on failure; the caller falls back to another engine. */
    suspend fun transcribe(request: TranscriptionRequest, onProgress: (Float) -> Unit): Transcript
}
