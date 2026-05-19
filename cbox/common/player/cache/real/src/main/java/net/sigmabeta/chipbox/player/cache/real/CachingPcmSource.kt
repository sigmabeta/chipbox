package net.sigmabeta.chipbox.player.cache.real

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmCacheKey
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.common.isBufferSilent
import net.sigmabeta.chipbox.player.common.maxAmplitude
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import java.io.RandomAccessFile

/**
 * Render-ahead [PcmTrackSource]. Owns:
 * - an [EmulatorPcmSource] running on a writer coroutine that races as fast as the CPU allows
 * - a [PcmCacheFile.Writer] receiving the writer's frames into `<key>.pcm.tmp`
 * - a separate [RandomAccessFile] read handle on the same temp file for the consumer
 * - a [watermark] [MutableStateFlow] that publishes "frames available to read so far"
 *
 * The Generator's read loop calls [readFrames]; if the requested range is already on disk,
 * the call is a direct file read. If the cursor has caught up to the watermark, [readFrames]
 * suspends on the watermark flow until the writer produces more (or surfaces an error if the
 * writer falls dramatically behind).
 *
 * On track end, the writer flips the header to "complete" and atomically renames `.pcm.tmp`
 * to `.pcm`. The next time this track plays, [RealPcmTrackSourceFactory] finds the complete
 * file and returns a [CachedFilePcmSource] instead of going through this class.
 */
internal class CachingPcmSource(
    private val emulatorSource: EmulatorPcmSource,
    private val writer: PcmCacheFile.Writer,
    private val track: Track,
    private val key: PcmCacheKey,
    private val hatchet: Hatchet,
    private val onWriteComplete: () -> Unit = {},
    private val onWriteAbort: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PcmTrackSource {

    override val sampleRate: Int = emulatorSource.sampleRate

    override val totalFrames: Long? = emulatorSource.totalFrames

    override val isOver: Boolean
        get() = writerComplete && cursor >= writer.framesWritten

    // Loudest sample across the rendered track; reported alongside the cache-write-complete
    // log and stamped into the header. 0 until the writer has produced audible audio, so a
    // first-time play (which reads this at track load) is left un-normalized.
    @Volatile
    private var measuredPeak = 0

    override val peakAmplitude: Int get() = measuredPeak

    private val writerScope = CoroutineScope(dispatcher)

    private val watermark = MutableStateFlow(0L)

    @Volatile
    private var writerComplete = false

    @Volatile
    private var writerError: String? = null

    @Volatile
    private var cursor: Long = 0L

    @Volatile
    private var lastError: String? = null

    private val readHandle: RandomAccessFile = RandomAccessFile(writer.tempPath, "r")

    private val writerJob: Job = writerScope.launch {
        val startNanos = System.nanoTime()
        val scratch = ShortArray(WRITER_BUFFER_FRAMES * 2)
        val zeroBuf = ShortArray(WRITER_BUFFER_FRAMES * 2)
        val silenceTrimFrames = SILENCE_TRIM_SECONDS * emulatorSource.sampleRate
        var pendingSilentFrames = 0L
        try {
            while (true) {
                // emulatorSource.readFrames and writer.appendFrames are synchronous, so without
                // an explicit cancellation point the loop never observes cancelAndJoin from
                // close() — the user's skip-track request would stall until the whole track
                // finished rendering.
                ensureActive()
                val framesGenerated = emulatorSource.readFrames(scratch)
                if (framesGenerated <= 0) {
                    if (emulatorSource.isTrackOver()) break
                    val emuError = emulatorSource.getLastError()
                    if (emuError != null) {
                        writerError = emuError
                        break
                    }
                    break
                }

                if (isBufferSilent(scratch, framesGenerated)) {
                    pendingSilentFrames += framesGenerated.toLong()
                    if (pendingSilentFrames >= silenceTrimFrames) {
                        if (writer.framesWritten == 0L) {
                            // We've now counted the full silence window without a single
                            // audible buffer, so this isn't trailing silence to trim — the
                            // track has produced no audio at all. Surface it instead of
                            // sealing an empty, "complete" cache entry that every future
                            // play would serve back instantly as a silent track.
                            writerError = "Track produced no audio within the first " +
                                "$SILENCE_TRIM_SECONDS seconds."
                        } else {
                            // Trailing silence — trim by leaving pendingSilentFrames
                            // unwritten and sealing the cache where the music actually ended.
                            hatchet.d(
                                "Cache trim for ${track.title}: dropping " +
                                    "$pendingSilentFrames trailing silent frames."
                            )
                        }
                        break
                    }
                    // Hold this silent buffer — only commit it if audible audio follows.
                } else {
                    if (pendingSilentFrames > 0) {
                        // Mid-track silent gap shorter than the trim threshold; persist it as
                        // zero frames so it plays back in the right spot.
                        writeZeroFrames(writer, zeroBuf, pendingSilentFrames)
                        watermark.value = writer.framesWritten
                        pendingSilentFrames = 0L
                    }
                    measuredPeak = maxOf(measuredPeak, maxAmplitude(scratch, framesGenerated))
                    writer.appendFrames(scratch, framesGenerated)
                    watermark.value = writer.framesWritten
                }
            }
            if (writerError == null) {
                writer.complete(track.id, track.trackLengthMs, measuredPeak)
                writerComplete = true
                watermark.value = writer.framesWritten
                logWriteComplete(startNanos)
                LoudnessLog.report(hatchet, track.title, measuredPeak)
                runCatching { onWriteComplete() }.onFailure {
                    hatchet.w("onWriteComplete callback failed: ${it.message}")
                }
            } else {
                writer.abort()
                // The watermark flow only re-checks its predicate on emission, and an
                // all-silent track never wrote a frame to bump it. Nudge it so a reader
                // parked in readFrames() wakes with this error rather than waiting out
                // READ_WAIT_TIMEOUT_MS and reporting the misleading "fell behind" timeout.
                watermark.value = -1L
                runCatching { onWriteAbort() }
            }
        } catch (e: CancellationException) {
            // Caller discarded this track mid-render. Drop the partial .pcm.tmp instead of
            // sealing it as complete; next play of this track will cache-miss and start fresh.
            writer.abort()
            runCatching { onWriteAbort() }
            throw e
        } catch (t: Throwable) {
            writerError = "Writer crash: ${t.message}"
            writer.abort()
            runCatching { onWriteAbort() }
        }
    }

    override suspend fun readFrames(buffer: ShortArray): Int {
        val maxFramesPerCall = (buffer.size / 2).toLong()
        val targetFrame = cursor + 1L

        // Wait for at least one frame past the cursor, or for the writer to declare completion.
        if (watermark.value < targetFrame && !writerComplete) {
            val arrived = withTimeoutOrNull(READ_WAIT_TIMEOUT_MS) {
                watermark.first { it >= targetFrame || writerComplete || writerError != null }
            }
            if (arrived == null) {
                lastError = "Cache writer fell behind reader (waited ${READ_WAIT_TIMEOUT_MS} ms)."
                return 0
            }
        }

        if (writerError != null) {
            lastError = writerError
            return 0
        }

        val available = (watermark.value - cursor).coerceAtMost(maxFramesPerCall).toInt()
        if (available <= 0) {
            // Writer is complete and we're at end-of-stream.
            return 0
        }

        val byteCount = available * net.sigmabeta.chipbox.player.cache.PcmCacheFormat.BYTES_PER_FRAME
        val byteOffset = net.sigmabeta.chipbox.player.cache.PcmCacheFormat.HEADER_SIZE_BYTES.toLong() +
            cursor * net.sigmabeta.chipbox.player.cache.PcmCacheFormat.BYTES_PER_FRAME
        val byteBuf = java.nio.ByteBuffer.allocate(byteCount).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        readHandle.channel.position(byteOffset)
        var totalRead = 0
        while (totalRead < byteCount) {
            val n = readHandle.channel.read(byteBuf)
            if (n < 0) break
            totalRead += n
        }
        byteBuf.flip()
        val shortsRead = totalRead / 2
        byteBuf.asShortBuffer().get(buffer, 0, shortsRead)
        val framesRead = shortsRead / 2
        cursor += framesRead.toLong()
        return framesRead
    }

    override suspend fun seek(framePosition: Long) {
        cursor = framePosition.coerceAtLeast(0L)
    }

    private fun logWriteComplete(startNanos: Long) {
        val frames = writer.framesWritten
        val wallSec = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
        val audioSec = if (sampleRate > 0) frames.toDouble() / sampleRate else 0.0
        val ratio = if (wallSec > 0) audioSec / wallSec else 0.0
        hatchet.i(
            "Cache write complete for ${track.title}: $frames frames " +
                "(${"%.1f".format(audioSec)}s audio) in ${"%.2f".format(wallSec)}s " +
                "(${"%.1f".format(ratio)}x realtime)."
        )
    }

    override fun getLastError(): String? = lastError ?: writerError

    override fun getDiagnostics(): String? = emulatorSource.getDiagnostics()

    override suspend fun close() {
        try {
            writerJob.cancelAndJoin()
        } finally {
            try {
                readHandle.close()
            } catch (_: Throwable) {
            }
            // The writer coroutine's finally block tears down the emulator and aborts/completes
            // the file. If we got cancelled before it ran, abort here as a safety net.
            if (!writerComplete) {
                writer.abort()
                runCatching { onWriteAbort() }
            }
            try {
                emulatorSource.close()
            } catch (_: Throwable) {
            }
            writerScope.coroutineContext[Job]?.cancel()
        }
    }

    private fun writeZeroFrames(
        writer: PcmCacheFile.Writer,
        zeroBuf: ShortArray,
        totalFrames: Long,
    ) {
        val maxPerWrite = (zeroBuf.size / 2).toLong()
        var remaining = totalFrames
        while (remaining > 0) {
            val chunk = minOf(remaining, maxPerWrite).toInt()
            writer.appendFrames(zeroBuf, chunk)
            remaining -= chunk.toLong()
        }
    }

    companion object {
        /** ~93 ms @ 44.1 kHz; same shape as the existing buffer manager so writer throughput
         *  isn't bottlenecked by tiny native calls. */
        private const val WRITER_BUFFER_FRAMES = 4096

        /** If the writer fails to produce a frame within this window, surface as an error. */
        private const val READ_WAIT_TIMEOUT_MS = 5_000L

        /** Trailing silence longer than this is dropped from the cache file. Mid-track silent
         *  gaps shorter than this still get persisted so the track plays back in time. */
        private const val SILENCE_TRIM_SECONDS = 5L

        /** Nanoseconds per second, for converting elapsed nanoTime to wall-clock seconds. */
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
