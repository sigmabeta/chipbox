package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.millisToFrames
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
abstract class Emulator {
    var trackOver: Boolean = false

    var nativeLibLoaded = false

    var hatchet: Hatchet = BluntHatchet()

    abstract fun loadNativeLib()

    abstract fun loadTrackInternal(path: String)

    abstract fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    abstract fun teardownInternal()

    abstract fun getLastError(): String?

    abstract fun getSampleRateInternal(): Int

    abstract val supportedFileExtensions: List<String>

    private var framesPlayedTotal = 0

    protected var remainingFramesTotal = Int.MAX_VALUE

    open fun isFileExtensionSupported(extension: String) =
        supportedFileExtensions.contains(extension)

    open fun setTrackNumber(number: Int) = Unit

    open fun loadTrack(track: Track) {
        hatchet.d("Loading track: ${track.title} (#${track.trackNumber}) from ${track.path}")
        if (remainingFramesTotal >= 0) {
            teardown()
        }

        setTrackNumber(track.trackNumber)
        loadTrackInternal(track.path)
        remainingFramesTotal =
            track.trackLengthMs.toDouble().millisToFrames(getSampleRateInternal())
    }

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
    }
}