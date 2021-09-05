package net.sigmabeta.chipbox.player.emulators

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.SHORTS_PER_FRAME
import net.sigmabeta.chipbox.player.common.millisToFrames

abstract class Emulator {
    var trackOver: Boolean = false

    abstract var sampleRate: Int

    abstract fun loadTrackInternal(track: Track)

    abstract fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    abstract fun teardownInternal()

    private var framesPlayedTotal = 0

    private var remainingFramesTotal = -1

    fun loadTrack(track: Track) {
        if (remainingFramesTotal >= 0) {
            println("Previously loaded emulator not cleared. Clearing...")
            teardown()
        }

        remainingFramesTotal = track.trackLengthMs.toDouble().millisToFrames(sampleRate)

        loadTrackInternal(track)
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

        remainingFramesTotal = -1
        framesPlayedTotal = 0
    }
}