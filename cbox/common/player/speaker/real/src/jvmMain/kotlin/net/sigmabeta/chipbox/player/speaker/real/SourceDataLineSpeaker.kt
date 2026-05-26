package net.sigmabeta.chipbox.player.speaker.real

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.sage.logging.Hatchet

/**
 * Real-time JVM/desktop [Speaker] backed by `javax.sound.sampled.SourceDataLine`. The line is
 * lazily (re)created whenever an incoming buffer's sample rate differs from the current line's,
 * matching `RealSpeaker`'s AudioTrack behaviour — emulators within a setlist may use different
 * rates, so the speaker can't commit to one format up front.
 *
 * Pause / resume map to `SourceDataLine.stop()` / `start()`; the line keeps its buffered audio
 * across `stop()` so resume picks up where it left off. `flushSink()` (for seek) drops the
 * buffered audio with `flush()` so post-seek frames don't sit behind a few hundred ms of
 * pre-seek output. Teardown drains, stops, and closes the line.
 *
 * `currentPositionMs` snapshots `(getMicrosecondPosition, audio.frameIndex)` on each received
 * buffer so the running position survives seeks (the line's microsecond counter resets only on
 * `close()`, not `flush()`). When no line is open (between teardown and the first new buffer)
 * it returns 0.
 *
 * Live audio on the JVM target — addresses roadmap item 2 in `docs/kmp-migration.md`. Heavy
 * emulator cores can emit a terminal `GeneratorEvent.Error` on a cold render-ahead cache; the
 * speaker survives this in the sense that it just stops getting buffers, but the user-visible
 * track will appear to halt. Tuning the render-ahead window is a separate change.
 */
class SourceDataLineSpeaker(
    bufferManager: ConsumerBufferManager,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseSpeaker(bufferManager, hatchet, dispatcher) {

    private var line: SourceDataLine? = null
    private var lineSampleRate: Int = 0
    private var conversionBuffer: ByteArray = ByteArray(0)

    // currentPositionMs reference points — snapshot of the line's microsecond counter when the
    // most recent buffer started, plus that buffer's track-relative frame index. Combined with
    // (now - reference) elapsed micros, this converts the line's monotonic counter into a
    // position within the song being played. Updated on every onAudioReceived so seeks +
    // track changes (where frameIndex jumps) are picked up.
    private var referenceMicrosecondPosition: Long = 0L
    private var referenceTrackFrame: Long = 0L

    override fun onAudioReceived(audio: AudioBuffer) {
        if (audio.sampleRate != lineSampleRate) {
            hatchet.d("New sample rate: ${audio.sampleRate}")
            openLine(audio.sampleRate)
        }

        val activeLine = line ?: return

        referenceMicrosecondPosition = activeLine.microsecondPosition
        referenceTrackFrame = audio.frameIndex

        val bytes = encodeLittleEndian(audio.data)
        activeLine.write(bytes, 0, audio.data.size * BYTES_PER_SAMPLE)
    }

    override fun currentPositionMs(): Long {
        val activeLine = line
        val rate = lineSampleRate
        if (activeLine == null || rate <= 0) return 0L
        val nowMicros = activeLine.microsecondPosition
        val elapsedMicros = (nowMicros - referenceMicrosecondPosition).coerceAtLeast(0L)
        val elapsedFrames = elapsedMicros * rate / MICROS_PER_SECOND
        val frame = referenceTrackFrame + elapsedFrames
        return frame * MILLIS_PER_SECOND / rate
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
        lineSampleRate = 0
        referenceMicrosecondPosition = 0L
        referenceTrackFrame = 0L

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
    }

    private fun openLine(sampleRate: Int) {
        teardown()

        val format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate.toFloat(),
            BITS_PER_SAMPLE,
            CHANNELS,
            BYTES_PER_FRAME,
            sampleRate.toFloat(),
            /* bigEndian = */
            false,
        )

        val opened = try {
            (AudioSystem.getSourceDataLine(format)).apply {
                open(format, LINE_BUFFER_BYTES)
                start()
            }
        } catch (ex: LineUnavailableException) {
            hatchet.e("SourceDataLine unavailable for ${sampleRate}Hz: ${ex.message}")
            emitError("Audio output unavailable: ${ex.message}")
            return
        }

        line = opened
        lineSampleRate = sampleRate
        hatchet.v(
            "Opened SourceDataLine: ${sampleRate}Hz, " +
                "buffer=${opened.bufferSize}B (~${opened.bufferSize / BYTES_PER_FRAME} frames).",
        )
    }

    /**
     * Convert the interleaved L/R 16-bit PCM [shorts] into a little-endian byte array the
     * `SourceDataLine` can consume. Reuses [conversionBuffer] across calls (the buffer pool
     * upstream owns the `data` array; this byte buffer is owned by this speaker, no aliasing
     * worry). Manual byte twiddle is ~3x faster than `ByteBuffer.putShort()` on hot paths.
     */
    private fun encodeLittleEndian(shorts: ShortArray): ByteArray {
        val byteCount = shorts.size * BYTES_PER_SAMPLE
        if (conversionBuffer.size < byteCount) {
            conversionBuffer = ByteArray(byteCount)
        }
        val out = conversionBuffer
        for (i in shorts.indices) {
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
        private const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS
        private const val BITS_PER_BYTE = 8
        private const val LOW_BYTE_MASK = 0xFF
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MICROS_PER_SECOND = 1_000_000L

        // ~85 ms of audio at 48 kHz stereo 16-bit. Big enough to absorb scheduling jitter on
        // the producer side without underrunning, small enough that pause/seek feel responsive.
        // SourceDataLine.flush() empties this on seek so it doesn't translate directly into
        // post-seek latency.
        private const val LINE_BUFFER_BYTES = 16_384
    }
}
