package com.arnoldcode.glassprompt.data.multimedia.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.arnoldcode.glassprompt.core.common.DispatcherProvider
import com.arnoldcode.glassprompt.core.common.IdGenerator
import com.arnoldcode.glassprompt.core.common.Logger
import com.arnoldcode.glassprompt.data.storage.MediaFileStore
import com.arnoldcode.glassprompt.domain.transcription.AudioExtractor
import com.arnoldcode.glassprompt.domain.transcription.EnergyVad
import com.arnoldcode.glassprompt.domain.transcription.ExtractedAudio
import com.arnoldcode.glassprompt.domain.transcription.LevelMeter
import com.arnoldcode.glassprompt.domain.transcription.MonoResampler
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Decodes the take's audio track with MediaCodec and streams it to disk as 16 kHz mono 16-bit
 * PCM — the format speech recognizers expect — measuring loudness for the VAD on the way.
 * Only one codec buffer is in memory at a time, so a long take costs no more RAM than a short one.
 */
class MediaCodecAudioExtractor @Inject constructor(
    private val files: MediaFileStore,
    private val ids: IdGenerator,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : AudioExtractor {

    override suspend fun extract(videoPath: String, onProgress: (Float) -> Unit): ExtractedAudio? = withContext(dispatchers.io) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val outFile = File(files.audioCacheDir, "audio_${ids.newId()}.pcm")
        var completed = false
        try {
            extractor.setDataSource(videoPath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val decoder = MediaCodec.createDecoderByType(checkNotNull(inputFormat.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var resampler = MonoResampler(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
            var floatPcm = false
            val meter = LevelMeter(SAMPLE_RATE * EnergyVad.FRAME_MS / 1000)
            var written = 0L
            var mono = FloatArray(0)
            val bytes = ByteArray(OUT_CHUNK_BYTES)

            BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { out ->
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buffer = checkNotNull(decoder.getInputBuffer(inIndex))
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            resampler = MonoResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)
                            floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        outIndex >= 0 -> {
                            val buffer = checkNotNull(decoder.getOutputBuffer(outIndex))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.nativeOrder())
                            val frames = buffer.remaining() / ((if (floatPcm) 4 else 2) * channels.coerceAtLeast(1))
                            if (mono.size < frames) mono = FloatArray(frames)
                            downmix(buffer, channels, floatPcm, frames, mono)
                            decoder.releaseOutputBuffer(outIndex, false)

                            val pcm = resampler.process(mono, frames)
                            meter.add(pcm)
                            written += writeLittleEndian(out, pcm, bytes)
                            if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            }
            onProgress(1f)
            completed = true
            ExtractedAudio(
                pcmPath = outFile.absolutePath,
                sampleRate = SAMPLE_RATE,
                durationMs = written * 1000 / SAMPLE_RATE,
                frameDbs = meter.finish(),
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            if (!completed) outFile.delete()
        }
    }

    override fun discard(audio: ExtractedAudio) {
        if (!File(audio.pcmPath).delete()) logger.w(TAG, "Could not delete ${audio.pcmPath}")
    }

    /** Averages [frames] interleaved frames of [buffer] into [mono]. */
    private fun downmix(buffer: ByteBuffer, channels: Int, floatPcm: Boolean, frames: Int, mono: FloatArray) {
        for (f in 0 until frames) {
            var acc = 0f
            repeat(channels) {
                acc += if (floatPcm) buffer.getFloat() else buffer.getShort() / 32768f
            }
            mono[f] = acc / channels
        }
    }

    /** Writes samples as little-endian bytes; returns how many samples were written. */
    private fun writeLittleEndian(out: BufferedOutputStream, pcm: ShortArray, bytes: ByteArray): Int {
        var i = 0
        while (i < pcm.size) {
            val n = minOf(pcm.size - i, bytes.size / 2)
            for (k in 0 until n) {
                val v = pcm[i + k].toInt()
                bytes[2 * k] = (v and 0xFF).toByte()
                bytes[2 * k + 1] = (v shr 8 and 0xFF).toByte()
            }
            out.write(bytes, 0, n * 2)
            i += n
        }
        return pcm.size
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val TIMEOUT_US = 10_000L
        private const val OUT_CHUNK_BYTES = 8 * 1024
        private const val TAG = "AudioExtractor"
    }
}
