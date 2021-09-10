package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.millisToFrames
abstract class Emulator {
    var trackOver: Boolean = false

    abstract var sampleRate: Int

    abstract fun loadTrackInternal(path: String)

    abstract fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    abstract fun teardownInternal()

    abstract fun getLastError(): String?

    private var framesPlayedTotal = 0

    protected var remainingFramesTotal = -1

    open fun loadTrack(track: Track) {
        if (remainingFramesTotal >= 0) {
            println("Previously loaded emulator not cleared. Clearing...")
            teardown()
        }

        remainingFramesTotal = track.trackLengthMs.toDouble().millisToFrames(sampleRate)

        loadTrackInternal(track.path)
    }

    fun generateBuffer(
        buffer: ShortArray
    ): Int {
        if (remainingFramesTotal < 0) {
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
        teardownInternal()

        trackOver = false
        remainingFramesTotal = -1
        framesPlayedTotal = 0
    }
}