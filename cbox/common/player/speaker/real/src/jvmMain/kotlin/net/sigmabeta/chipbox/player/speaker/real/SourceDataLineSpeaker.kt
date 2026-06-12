package net.sigmabeta.chipbox.player.speaker.real

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.player.resampler.ResamplerDebugInfo
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet

/**
 * Real-time JVM/desktop [Speaker] backed by `javax.sound.sampled.SourceDataLine`.
 *
 * Sample-rate handling is fixed for the speaker's lifetime by the injected [resampler], resolved
 * from the user's saved resampler setting when the graph builds it (changing the setting takes
 * effect on the next launch — there is no live switching). A non-null [resampler] means in-app
 * resampling: the line is opened at the device rate ([outputSampleRateHz]) and each buffer is
 * converted to it by the chosen kernel, not the OS mixer. A null [resampler] is OS mode: the line is
 * opened at the emulator's native rate and `javax.sound`/the OS mixer resamples. The line is rebuilt
 * when the input rate changes.
 *
 * Pause / resume map to `SourceDataLine.stop()` / `start()`; the line keeps its buffered audio
 * across `stop()` so resume picks up where it left off. `flushSink()` (for seek) drops the
 * buffered audio with `flush()` and resets the resampler so post-seek frames don't sit behind a few
 * hundred ms of pre-seek output. Teardown drains, stops, and closes the line.
 *
 * `readSinkPositionMs` snapshots `(getMicrosecondPosition, audio.frameIndex)` on each received
 * buffer so the running position survives seeks (the line's microsecond counter resets only on
 * `close()`, not `flush()`). The microsecond counter measures real elapsed playback time, so the
 * position is correct whether or not we're resampling. When no line is open (between teardown and
 * the first new buffer) it returns 0. It runs only on the consume coroutine; the value the director
 * reads is published through `BaseSpeaker.currentPositionMs`.
 */
class SourceDataLineSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
    private val resampler: Resampler?,
    private val outputSampleRateHz: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseSpeaker(bufferManager, hatchet, dispatcher) {

    // @Volatile: written on the consume coroutine (openLine) and on the lifecycle caller's thread
    // (teardown, after the consume loop is cancel-joined). The visibility guard keeps a stale
    // non-null `line` from being observed across that handoff.
    @Volatile
    private var line: SourceDataLine? = null

    private var conversionBuffer: ByteArray = ByteArray(0)

    // Native rate of the audio currently flowing (the cache/emulator rate), distinct from the line's
    // output rate. Touched only on the consume coroutine. Drives when to rebuild the line.
    private var currentInputRate: Int = 0

    // The line's open rate: the device rate when resampling, the native rate in OS mode.
    private var currentOutputRate: Int = 0

    // Scratch output buffer grown on demand and reused across buffers.
    private var resampleBuffer: ShortArray = ShortArray(0)

    // currentPositionMs reference points — snapshot of the line's microsecond counter when the
    // most recent buffer started, plus that buffer's track-relative frame index. Combined with
    // (now - reference) elapsed micros, this converts the line's monotonic counter into a
    // position within the song being played. Updated on every onAudioReceived so seeks +
    // track changes (where frameIndex jumps) are picked up.
    private var referenceMicrosecondPosition: Long = 0L
    private var referenceTrackFrame: Long = 0L

    override fun onAudioReceived(audio: AudioBuffer) {
        // Rebuild when the input rate changes or the line was torn down (null). The resampler is
        // fixed for the speaker's life, so the line's open rate only depends on the input rate.
        if (line == null || audio.sampleRate != currentInputRate) {
            currentInputRate = audio.sampleRate
            // OS mode (null resampler) keeps the native rate and lets the OS mixer resample;
            // otherwise open at the device rate and resample in-app to it.
            val outputRate = if (resampler == null) audio.sampleRate else outputSampleRateHz
            currentOutputRate = outputRate
            openLine(outputRate)
            resampler?.reset()
            hatchet.i(
                "SourceDataLine setup: input ${audio.sampleRate} Hz -> output $outputRate Hz " +
                    "(resampling=${isResampling()}).",
            )
            updateResamplerDebug(
                ResamplerDebugInfo(
                    mode = resampler?.let { it::class.simpleName } ?: "OS",
                    active = isResampling(),
                    inputRateHz = audio.sampleRate,
                    outputRateHz = outputRate,
                ),
            )
        }

        val activeLine = line ?: return

        referenceMicrosecondPosition = activeLine.microsecondPosition
        referenceTrackFrame = audio.frameIndex

        writeToLine(audio, activeLine)
    }

    /** True when an in-app resampler is engaged — a kernel is set and the rates actually differ. */
    private fun isResampling(): Boolean = resampler != null && currentInputRate != currentOutputRate

    /** Write [audio] to [activeLine], resampling to the line's output rate first when [isResampling]. */
    private fun writeToLine(audio: AudioBuffer, activeLine: SourceDataLine) {
        val converter = resampler
        val shorts: ShortArray
        val sampleCount: Int
        if (converter == null || !isResampling()) {
            shorts = audio.data
            sampleCount = audio.data.size
        } else {
            val inputFrames = audio.data.size / SHORTS_PER_FRAME
            val neededShorts = converter.maxOutputFrames(inputFrames, currentInputRate, currentOutputRate) * SHORTS_PER_FRAME
            if (resampleBuffer.size < neededShorts) resampleBuffer = ShortArray(neededShorts)
            sampleCount = converter.process(
                audio.data,
                inputFrames,
                currentInputRate,
                currentOutputRate,
                resampleBuffer,
            ) * SHORTS_PER_FRAME
            shorts = resampleBuffer
        }

        val bytes = encodeLittleEndian(shorts, sampleCount)
        activeLine.write(bytes, 0, sampleCount * BYTES_PER_SAMPLE)
    }

    override fun readSinkPositionMs(): Long {
        val activeLine = line
        val rate = currentInputRate
        if (activeLine == null || rate <= 0) return 0L
        // microsecondPosition measures real elapsed playback time, so it converts to song position
        // directly — no dependence on the line's (possibly resampled) output rate. referenceTrackFrame
        // is in input (track-native) frames, hence the input rate here.
        val nowMicros = activeLine.microsecondPosition
        val elapsedMicros = (nowMicros - referenceMicrosecondPosition).coerceAtLeast(0L)
        return referenceTrackFrame * MILLIS_PER_SECOND / rate + elapsedMicros / MICROS_PER_MILLI
    }

    override fun onPaused() {
        line?.stop()
    }

    override fun onResumed() {
        line?.start()
    }

    override fun teardown() {
        val current = line ?: return
        line = null
        referenceMicrosecondPosition = 0L
        referenceTrackFrame = 0L
        updateResamplerDebug(null)

        hatchet.i("Tearing down SourceDataLine.")
        runCatching {
            // No drain(): on Linux backends (PipeWire/ALSA via javax.sound) drain() can hang
            // indefinitely after a flushed or about-to-change-rate line, wedging the consume
            // loop. We drop whatever's still in the line's ~LINE_BUFFER_BYTES hardware buffer
            // (~tens of ms) — fine for a stop (the user wanted silence) and a fast cut at rate
            // transitions, where any tail audio would have to be torn up anyway.
            current.stop()
            current.close()
        }.onFailure { hatchet.w("SourceDataLine teardown threw: ${it.message}") }
    }

    override fun flushSink() {
        val activeLine = line ?: return
        // SourceDataLine.flush() while running discards the line's internal buffered audio
        // without affecting its microsecond counter — what we want when the producer side has
        // already drained pre-seek buffers and the post-seek frames are about to arrive.
        hatchet.d("flushSink: SourceDataLine.flush()")
        activeLine.flush()
        // Drop the resampler's phase + history so post-seek frames don't interpolate across the gap.
        resampler?.reset()
    }

    private fun openLine(outputRate: Int) {
        teardown()

        val format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            outputRate.toFloat(),
            BITS_PER_SAMPLE,
            CHANNELS,
            BYTES_PER_FRAME,
            outputRate.toFloat(),
            /* bigEndian = */
            false,
        )

        val opened = try {
            (AudioSystem.getSourceDataLine(format)).apply {
                open(format, LINE_BUFFER_BYTES)
                start()
            }
        } catch (ex: LineUnavailableException) {
            hatchet.e("SourceDataLine unavailable for ${outputRate}Hz: ${ex.message}")
            emitError("Audio output unavailable: ${ex.message}")
            return
        }

        line = opened
        hatchet.v(
            "Opened SourceDataLine: ${outputRate}Hz, " +
                "buffer=${opened.bufferSize}B (~${opened.bufferSize / BYTES_PER_FRAME} frames).",
        )
    }

    /**
     * Convert the first [sampleCount] interleaved L/R 16-bit PCM samples of [shorts] into a
     * little-endian byte array the `SourceDataLine` can consume. [sampleCount] can be smaller than
     * `shorts.size` because the resample scratch buffer is sized to a worst-case bound and reused.
     * Reuses [conversionBuffer] across calls (the buffer pool upstream owns the `data` array; this
     * byte buffer is owned by this speaker, no aliasing worry). Manual byte twiddle is ~3x faster
     * than `ByteBuffer.putShort()` on hot paths.
     */
    private fun encodeLittleEndian(shorts: ShortArray, sampleCount: Int): ByteArray {
        val byteCount = sampleCount * BYTES_PER_SAMPLE
        if (conversionBuffer.size < byteCount) {
            conversionBuffer = ByteArray(byteCount)
        }
        val out = conversionBuffer
        for (i in 0 until sampleCount) {
            val s = shorts[i].toInt()
            out[i * BYTES_PER_SAMPLE] = (s and LOW_BYTE_MASK).toByte()
            out[i * BYTES_PER_SAMPLE + 1] = ((s shr BITS_PER_BYTE) and LOW_BYTE_MASK).toByte()
        }
        return out
    }

    private companion object {
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = 2
        private const val CHANNELS = 2
        private const val SHORTS_PER_FRAME = 2
        private const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS
        private const val BITS_PER_BYTE = 8
        private const val LOW_BYTE_MASK = 0xFF
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MICROS_PER_MILLI = 1_000L

        // ~85 ms of audio at 48 kHz stereo 16-bit. Big enough to absorb scheduling jitter on
        // the producer side without underrunning, small enough that pause/seek feel responsive.
        // SourceDataLine.flush() empties this on seek so it doesn't translate directly into
        // post-seek latency.
        private const val LINE_BUFFER_BYTES = 16_384
    }
}
