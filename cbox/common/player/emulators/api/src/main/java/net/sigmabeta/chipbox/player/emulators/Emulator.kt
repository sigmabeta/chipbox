package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.millisToFrames
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet

/**
 * Base class for every chiptune backend the player can drive — concrete subclasses wrap native
 * libraries (GME, GBA, PSF, SSF, 2SF, ...) or the in-process [fake.FakeEmulator] sine/square
 * synth.
 *
 * The contract is intentionally minimal: load a track, then repeatedly fill a PCM buffer until
 * the configured length is reached. The [Generator] supplies the buffers and the playback
 * scheduling on top.
 *
 * ### Track-length tracking
 * Most chiptune formats loop forever — there is no "end of file." This base class converts the
 * track's declared length to a frame budget ([remainingFramesTotal]) and short-circuits
 * [generateBuffer] once the budget is exhausted, which is what causes [trackOver] to flip.
 *
 * ### Threading
 * All methods run on the [Generator]'s loop coroutine. Subclasses can assume single-threaded
 * access; nothing else in the player touches an emulator instance.
 *
 * ### Native library loading
 * [nativeLibLoaded] is the lazy-init flag the generator checks before calling [loadNativeLib];
 * subclasses don't manage it themselves. Once loaded, the library stays loaded for the
 * process lifetime.
 */
abstract class Emulator {
    /** Set true by [generateBuffer] when the per-track frame budget is exhausted. The
     *  generator polls this between buffers to know when to advance the setlist. */
    var trackOver: Boolean = false

    /** Lazy-init guard; managed by the generator, not the emulator subclass. */
    var nativeLibLoaded = false

    /** Per-track logger; injected by the generator so emulator log lines carry the right
     *  context. Defaults to a no-op so unit tests don't need to wire one up. */
    var hatchet: Hatchet = BluntHatchet()

    /** Load the JNI library backing this emulator. Called at most once per process. */
    abstract fun loadNativeLib()

    /** Hand the staged file path to the native side and prepare it for [generateBufferInternal]. */
    abstract fun loadTrackInternal(path: String)

    /** Fill [buffer] (interleaved L/R 16-bit PCM) with up to [framesPerBuffer] frames.
     *  Returns the number of frames actually produced. */
    abstract fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    /** Release per-track native resources. Called from [teardown]. */
    abstract fun teardownInternal()

    /** Most-recent error from the native side, or null. */
    abstract fun getLastError(): String?

    /** Non-fatal diagnostics accumulated during the most recent [generateBuffer] call (e.g.
     *  IOP HLE warnings from PSF2). Default null; subclasses opt in. */
    open fun getDiagnostics(): String? = null

    /** Native output sample rate for the loaded track, in Hz. */
    abstract fun getSampleRateInternal(): Int

    /** File extensions this emulator can decode (lowercase, without leading dot). */
    abstract val supportedFileExtensions: List<String>

    private var framesPlayedTotal = 0

    /** Frames left in the track's declared length budget. Sentinel [Int.MAX_VALUE] before a
     *  track is loaded; goes negative once exhausted. */
    protected var remainingFramesTotal = Int.MAX_VALUE

    private var hasLoadedTrack = false

    open fun isFileExtensionSupported(extension: String) =
        supportedFileExtensions.contains(extension)

    /** Selects a sub-track for multi-track formats (NSF, GBS, etc). No-op for single-track
     *  formats, which is the default. */
    open fun setTrackNumber(number: Int) = Unit

    /**
     * Load [track], computing the frame budget from its declared length so [generateBuffer]
     * knows when to stop. Tears down any previously-loaded track first.
     */
    open fun loadTrack(track: Track) {
        hatchet.d("Loading track: ${track.title} (#${track.trackNumber}) from ${track.path}")
        if (hasLoadedTrack) {
            teardown()
        }

        setTrackNumber(track.trackNumber)
        loadTrackInternal(track.path)
        remainingFramesTotal =
            track.trackLengthMs.toDouble().millisToFrames(getSampleRateInternal())
        hasLoadedTrack = true
    }

    /**
     * Generate one buffer of audio. Tracks the running frame count and decrements the budget;
     * once the budget hits zero, [trackOver] is set and `-1` is returned to signal end-of-track
     * to the generator.
     */
    fun generateBuffer(
        buffer: ShortArray
    ): Int {
        if (remainingFramesTotal < 0) {
            hatchet.d("Track is over.")
            trackOver = true
            return -1
        }

        val framesPerBuffer = buffer.size / SHORTS_PER_FRAME

        val framesPlayed = generateBufferInternal(buffer, framesPerBuffer)

        framesPlayedTotal += framesPlayed
        remainingFramesTotal -= framesPlayed

        return framesPlayed
    }

    fun teardown() {
        hatchet.d("Tearing down emulator.")
        teardownInternal()

        trackOver = false
        remainingFramesTotal = Int.MAX_VALUE
        framesPlayedTotal = 0
        hasLoadedTrack = false
    }
}
