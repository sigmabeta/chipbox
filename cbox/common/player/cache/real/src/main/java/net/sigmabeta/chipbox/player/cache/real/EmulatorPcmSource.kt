package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

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
    private val stagedFile: File,
    private val stagingTrackDir: File,
    private val hatchet: Hatchet,
) : PcmTrackSource {

    override val sampleRate: Int

    override val totalFrames: Long?

    private var lastError: String? = null

    init {
        if (!emulator.nativeLibLoaded) {
            hatchet.i("Loading native lib for ${emulator::class.simpleName}.")
            emulator.loadNativeLib()
            emulator.nativeLibLoaded = true
        }
        emulator.hatchet = hatchet
        emulator.setTrackNumber(track.trackNumber)
        emulator.loadTrack(track.copy(path = stagedFile.absolutePath))

        sampleRate = emulator.getSampleRateInternal()
        totalFrames = if (track.trackLengthMs > 0) {
            (track.trackLengthMs.toDouble() * sampleRate / MILLIS_PER_SECOND).toLong()
        } else {
            null
        }
    }

    override suspend fun readFrames(buffer: ShortArray): Int {
        val framesGenerated = emulator.generateBuffer(buffer)
        lastError = emulator.getLastError()
        return if (framesGenerated < 0) 0 else framesGenerated
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
            stagingTrackDir.deleteRecursively()
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
