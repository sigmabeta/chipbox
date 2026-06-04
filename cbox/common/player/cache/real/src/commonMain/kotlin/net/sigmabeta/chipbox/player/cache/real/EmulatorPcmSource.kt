package net.sigmabeta.chipbox.player.cache.real

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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
 *
 * ### Threading
 * Every native-emulator call this class makes ([readFrames], [close]) is confined to
 * [emulatorDispatcher] — the single thread that the process-wide-singleton emulators must be
 * driven from. The constructor itself calls into native ([Emulator.loadTrack]), so the factory
 * **must construct this on [emulatorDispatcher]** too; otherwise a load could race another track's
 * in-flight generate on the shared core.
 */
internal class EmulatorPcmSource(
    private val emulator: Emulator,
    private val track: Track,
    private val stagedFile: Path,
    private val stagingTrackDir: Path,
    private val fileSystem: FileSystem,
    private val hatchet: Hatchet,
    private val emulatorDispatcher: CoroutineDispatcher,
) : PcmTrackSource {

    override val sampleRate: Int

    override val totalFrames: Long?

    @Volatile
    private var lastError: String? = null

    // Captured on the emulator thread during [readFrames] so [getDiagnostics] (called from the
    // generator loop, a different thread) never reaches into native off-thread.
    @Volatile
    private var diagnostics: String? = null

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
        // Generate + read native state on the emulator thread, atomically with respect to any
        // teardown/load of the shared singleton core (which are confined to the same thread).
        val framesGenerated = withContext(emulatorDispatcher) {
            val frames = emulator.generateBuffer(buffer)
            lastError = emulator.getLastError()
            diagnostics = emulator.getDiagnostics()
            frames
        }
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

    override fun getDiagnostics(): String? = diagnostics

    override val isOver: Boolean get() = emulator.trackOver

    fun isTrackOver(): Boolean = emulator.trackOver

    override suspend fun close() {
        try {
            // Confined to the emulator thread so the free can't race an in-flight generate of the
            // shared singleton core (the use-after-free that crashed the native cores on skip).
            withContext(emulatorDispatcher) { emulator.teardown() }
        } finally {
            fileSystem.deleteRecursively(stagingTrackDir, mustExist = false)
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
