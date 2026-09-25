package com.arnoldcode.glassprompt.domain.transcription

/** Decoded, speech-ready audio of a take: 16-bit mono PCM on disk plus its loudness per VAD frame. */
class ExtractedAudio(
    val pcmPath: String,
    val sampleRate: Int,
    val durationMs: Long,
    /** Level of each [EnergyVad.FRAME_MS] frame, in dBFS. */
    val frameDbs: DoubleArray,
)

/** Decodes a video's audio track. Implemented with MediaCodec in the data layer. */
interface AudioExtractor {
    /** Null when the video has no audio track (e.g. recorded without microphone permission). */
    suspend fun extract(videoPath: String, onProgress: (Float) -> Unit): ExtractedAudio?

    /** Deletes the temporary PCM file. */
    fun discard(audio: ExtractedAudio)
}

/**
 * Streaming mono resampler. Downsampling averages every input sample that falls into an
 * output period (a box filter), which keeps high-frequency noise from folding into the speech
 * band; upsampling interpolates linearly. Feed it any chunk size; state carries over.
 */
class MonoResampler(inputRate: Int, private val outputRate: Int) {

    /** Input samples per output sample. */
    private val step = inputRate.toDouble() / outputRate
    private val downsampling = step >= 1.0

    // Downsampling state.
    private var sum = 0.0
    private var count = 0
    private var inputIndex = 0L
    private var nextBoundary = step

    // Upsampling state.
    private var previous = 0f
    private var hasPrevious = false
    private var outputPosition = 0.0

    /** Consumes [count] samples in [-1, 1] and returns the resampled 16-bit samples. */
    fun process(samples: FloatArray, count: Int = samples.size): ShortArray {
        val out = ShortArray((count / step).toInt() + 2)
        var n = 0
        for (i in 0 until count) {
            val x = samples[i]
            if (downsampling) {
                sum += x
                this.count++
                inputIndex++
                if (inputIndex >= nextBoundary) {
                    out[n++] = toPcm16(sum / this.count)
                    sum = 0.0
                    this.count = 0
                    nextBoundary += step
                }
            } else {
                if (!hasPrevious) {
                    previous = x
                    hasPrevious = true
                    inputIndex = 0
                    continue
                }
                inputIndex++
                // Emit every output instant between the previous and this input sample.
                while (outputPosition <= inputIndex) {
                    val t = outputPosition - (inputIndex - 1)
                    if (n == out.size) break
                    out[n++] = toPcm16(previous + (x - previous) * t)
                    outputPosition += step
                }
                previous = x
            }
        }
        return out.copyOf(n)
    }

    private fun toPcm16(value: Double): Short = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
}

/** Accumulates 16-bit samples into fixed frames and records each frame's level in dBFS. */
class LevelMeter(private val frameSamples: Int) {
    private val levels = ArrayList<Double>()
    private val frame = ShortArray(frameSamples)
    private var filled = 0

    fun add(samples: ShortArray, count: Int = samples.size) {
        for (i in 0 until count) {
            frame[filled++] = samples[i]
            if (filled == frameSamples) {
                levels += EnergyVad.frameDb(frame)
                filled = 0
            }
        }
    }

    /** Levels of all complete frames, plus the trailing partial one. */
    fun finish(): DoubleArray {
        if (filled > 0) {
            levels += EnergyVad.frameDb(frame, 0, filled)
            filled = 0
        }
        return levels.toDoubleArray()
    }
}
