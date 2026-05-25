package net.sigmabeta.chipbox.player.cache.real

import kotlin.concurrent.Volatile
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.common.EbuR128
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path

/**
 * Live-emulator-backed [PcmTrackSource]. Frames are produced one buffer at a time by the
 * underlying [Emulator]; [seek] is not supported (chiptune emulators have no fast-forward).
 *
 * Construction takes a [stagedFile] path that the caller has already prepared (source bytes
 * plus any chain files written into a real filesystem directory the native emulator can read).
 * The factory does staging because chain-file resolution needs the suspending
 * [net.sigmabeta.chipbox.contentsource.ContentSource.openBytes].
 *
 * On [close], the source tears down the emulator and deletes its staging directory.
 */
internal class EmulatorPcmSource(
    private val emulator: Emulator,
    private val track: Track,
    private val stagedFile: Path,
    private val stagingTrackDir: Path,
    private val fileSystem: FileSystem,
    private val hatchet: Hatchet,
) : PcmTrackSource {

    override val sampleRate: Int

    override val totalFrames: Long?

    private var lastError: String? = null

    // Live BS.1770 measurer. Without render-ahead, the measurement tracks the playback position —
    // exposed via [loudnessLufs] / [truePeakDbtp] for the speaker's progressive normalization.
    private val measurer: EbuR128

    @Volatile
    private var measuredLufs: Double = Double.NaN

    @Volatile
    private var measuredTruePeakDbtp: Double = Double.NEGATIVE_INFINITY

    override val loudnessLufs: Double get() = measuredLufs

    override val truePeakDbtp: Double get() = measuredTruePeakDbtp

    init {
        if (!emulator.nativeLibLoaded) {
            hatchet.i("Loading native lib for ${emulator::class.simpleName}.")
            emulator.loadNativeLib()
            emulator.nativeLibLoaded = true
        }
        emulator.hatchet = hatchet
        emulator.setTrackNumber(track.trackNumber)
        emulator.loadTrack(track.copy(path = stagedFile.toString()))

        sampleRate = emulator.getSampleRateInternal()
        totalFrames = if (track.trackLengthMs > 0) {
            (track.trackLengthMs.toDouble() * sampleRate / MILLIS_PER_SECOND).toLong()
        } else {
            null
        }
        measurer = EbuR128(sampleRate)
    }

    override suspend fun readFrames(buffer: ShortArray): Int {
        val framesGenerated = emulator.generateBuffer(buffer)
        lastError = emulator.getLastError()
        if (framesGenerated <= 0) return 0
        measurer.process(buffer, framesGenerated)
        measuredLufs = measurer.integratedLoudness()
        measuredTruePeakDbtp = measurer.truePeakDbtp()
        return framesGenerated
    }

    override suspend fun seek(framePosition: Long) {
        // Live emulators don't support arbitrary seeking — that's the whole point of caching.
        // The wrapping CachingPcmSource handles seek against the cache file instead.
        throw UnsupportedOperationException(
            "EmulatorPcmSource does not support seek; wrap in CachingPcmSource."
        )
    }

    override fun getLastError(): String? = lastError

    override fun getDiagnostics(): String? = emulator.getDiagnostics()

    override val isOver: Boolean get() = emulator.trackOver

    fun isTrackOver(): Boolean = emulator.trackOver

    override suspend fun close() {
        try {
            emulator.teardown()
        } finally {
            fileSystem.deleteRecursively(stagingTrackDir, mustExist = false)
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
